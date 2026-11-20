package com.cocos.challenge.application;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.exceptions.MarketDataUnavailableException;
import com.cocos.challenge.domain.exceptions.UserNotFoundException;
import com.cocos.challenge.domain.models.*;
import com.cocos.challenge.domain.ports.in.GetPortfolioPort;
import com.cocos.challenge.domain.ports.out.InstrumentPersistencePort;
import com.cocos.challenge.domain.ports.out.MarketDataPersistencePort;
import com.cocos.challenge.domain.ports.out.OrderPersistencePort;
import com.cocos.challenge.domain.ports.out.UserPersistencePort;
import com.cocos.challenge.domain.strategies.CalculatePositionsContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GetPortfolioUseCase implements GetPortfolioPort {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String CURRENCY = "ARS";

    private final UserPersistencePort users;
    private final InstrumentPersistencePort instruments;
    private final OrderPersistencePort orders;
    private final MarketDataPersistencePort marketData;
    private final CalculatePositionsContext calculatePositionsContext;

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PortfolioModel execute(int userId) {
        users.findById(userId).orElseThrow(() -> new UserNotFoundException("User " + userId + " not found"));
        List<OrderModel> orders = this.orders.findByUserOrderedByDatetime(userId);
        PositionsModel positions = calculatePositionsContext.apply(orders);
        List<Integer> heldIds = positions.holdings().values().stream()
                .filter(holding -> holding.quantity().signum() != 0)
                .map(HoldingModel::instrumentId)
                .toList();
        Map<Integer, InstrumentModel> heldInstruments = instruments.findByIds(heldIds);
        Map<Integer, MarketDataModel> quotes = marketData.findLatest(heldIds);
        return this.buildPortfolio(userId, positions, heldInstruments, quotes);
    }

    private PortfolioModel buildPortfolio(int userId, PositionsModel positions, Map<Integer, InstrumentModel> instruments,
                                          Map<Integer, MarketDataModel> latestQuotes) {
        List<PortfolioPositionModel> result = new ArrayList<>();
        BigDecimal totalValue = positions.settledCash();

        List<HoldingModel> held = positions.holdings().values().stream()
                .filter(holding -> holding.quantity().signum() != 0)
                .sorted(Comparator.comparingInt(HoldingModel::instrumentId))
                .toList();

        for (HoldingModel holding : held) {
            InstrumentModel instrument = instruments.get(holding.instrumentId());
            if (instrument == null) {
                throw new InconsistentDataException("Unknown instrument " + holding.instrumentId());
            }
            MarketDataModel quote = latestQuotes.get(holding.instrumentId());
            if (quote == null || notPositive(quote.close()) || notPositive(quote.previousClose())) {
                throw new MarketDataUnavailableException(
                        "No valid quote for instrument " + holding.instrumentId());
            }

            BigDecimal marketValue = holding.quantity().multiply(quote.close());
            BigDecimal totalReturn = holding.remainingCost().signum() == 0
                    ? BigDecimal.ZERO
                    : marketValue.subtract(holding.remainingCost())
                    .divide(holding.remainingCost().abs(), MathContext.DECIMAL128).multiply(HUNDRED);
            BigDecimal dailyReturn = quote.close().subtract(quote.previousClose())
                    .divide(quote.previousClose(), MathContext.DECIMAL128).multiply(HUNDRED);

            totalValue = totalValue.add(marketValue);
            result.add(PortfolioPositionModel.builder()
                    .instrumentId(instrument.id())
                    .ticker(instrument.ticker())
                    .name(instrument.name())
                    .quantity(holding.quantity().setScale(0, RoundingMode.UNNECESSARY))
                    .marketPrice(quote.close())
                    .marketValue(marketValue)
                    .totalReturnPercent(totalReturn)
                    .dailyReturnPercent(dailyReturn)
                    .build());
        }

        return PortfolioModel.builder()
                .userId(userId)
                .currency(CURRENCY)
                .totalValue(totalValue)
                .availableCash(positions.settledCash().subtract(positions.reservedCash()))
                .positions(Collections.unmodifiableList(result))
                .build();
    }

    private boolean notPositive(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }
}
