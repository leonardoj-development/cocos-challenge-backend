package com.cocos.challenge.application;

import com.cocos.challenge.domain.exceptions.*;
import com.cocos.challenge.domain.models.*;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.ports.in.PlaceOrderPort;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.domain.ports.out.InstrumentPersistencePort;
import com.cocos.challenge.domain.ports.out.MarketDataPersistencePort;
import com.cocos.challenge.domain.ports.out.OrderPersistencePort;
import com.cocos.challenge.domain.ports.out.UserPersistencePort;
import com.cocos.challenge.domain.strategies.CalculatePositionsContext;
import com.cocos.challenge.domain.strategies.CheckOrderRejectionContext;
import com.cocos.challenge.domain.strategies.PlaceOrderContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceOrderUseCase implements PlaceOrderPort {
    private static final String CASH_TYPE = "MONEDA";

    private final UserPersistencePort users;
    private final InstrumentPersistencePort instruments;
    private final OrderPersistencePort orders;
    private final MarketDataPersistencePort marketData;
    private final PlaceOrderContext placeOrderContext;
    private final CalculatePositionsContext calculatePositionsContext;
    private final CheckOrderRejectionContext checkOrderRejectionContext;
    private final Clock clock;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public PlaceOrderResultModel execute(int userId, PlaceOrderCommand command) {
        return place(userId, command);
    }

    private PlaceOrderResultModel place(int userId, PlaceOrderCommand command) {
        users.lockById(userId).orElseThrow(() -> new UserNotFoundException("User " + userId + " not found"));

        InstrumentModel instrument = instruments.findById(command.instrumentId()).orElseThrow(
                () -> new InstrumentNotFoundException("Instrument " + command.instrumentId() + " not found"));
        if (CASH_TYPE.equals(instrument.type())) {
            throw new InvalidOrderException("Currency instruments cannot be traded");
        }
        requireSingle(instruments.findArsInstruments());

        BigDecimal marketClose = OrderTypeEnum.MARKET.equals(command.type()) ? latestClose(command) : null;

        PlaceOrderDecisionModel decision = placeOrderContext.apply(command, marketClose);
        PositionsModel positions = calculatePositionsContext.apply(orders.findByUserOrderedByDatetime(userId));
        RejectionReasonEnum rejection = checkOrderRejectionContext.apply(command, decision, positions);
        OrderStatusEnum status = rejection != null ? OrderStatusEnum.REJECTED : decision.status();

        OrderModel saved = orders.insert(NewOrderModel.builder()
                .userId(userId)
                .instrumentId(instrument.id())
                .side(command.side())
                .size(decision.quantity())
                .price(decision.price())
                .type(command.type())
                .status(status)
                .datetime(Instant.now(clock))
                .build());
        return PlaceOrderResultModel.builder()
                .order(saved)
                .rejectionReason(rejection)
                .build();
    }

    private BigDecimal latestClose(PlaceOrderCommand command) {
        BigDecimal close = marketData.findLatest(command.instrumentId())
                .map(MarketDataModel::close)
                .orElseThrow(() -> new MarketDataUnavailableException(
                        "No quote for instrument " + command.instrumentId()));
        if (close.signum() <= 0) {
            throw new MarketDataUnavailableException("Invalid close for instrument " + command.instrumentId());
        }
        return close;
    }

    private void requireSingle(List<InstrumentModel> arsInstruments) {
        if (arsInstruments.size() != 1) {
            throw new InconsistentDataException(
                    "Expected exactly one ARS instrument but found " + arsInstruments.size());
        }
    }
}
