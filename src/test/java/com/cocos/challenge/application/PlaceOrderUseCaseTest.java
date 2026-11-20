package com.cocos.challenge.application;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.exceptions.InstrumentNotFoundException;
import com.cocos.challenge.domain.exceptions.InvalidOrderException;
import com.cocos.challenge.domain.exceptions.MarketDataUnavailableException;
import com.cocos.challenge.domain.exceptions.UserNotFoundException;
import com.cocos.challenge.domain.models.InstrumentModel;
import com.cocos.challenge.domain.models.MarketDataModel;
import com.cocos.challenge.domain.models.NewOrderModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.PlaceOrderResultModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.UserModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.domain.ports.out.InstrumentPersistencePort;
import com.cocos.challenge.domain.ports.out.MarketDataPersistencePort;
import com.cocos.challenge.domain.ports.out.OrderPersistencePort;
import com.cocos.challenge.domain.ports.out.UserPersistencePort;
import com.cocos.challenge.domain.strategies.CalculatePositionsContext;
import com.cocos.challenge.domain.strategies.CheckOrderRejectionContext;
import com.cocos.challenge.domain.strategies.PlaceOrderContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Place order use case")
class PlaceOrderUseCaseTest {
    private static final int USER_ID = 1;
    private static final int ASSET = 2;
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final BigDecimal CLOSE = new BigDecimal("80");
    private static final PositionsModel POSITIONS = PositionsModel.builder().settledCash(new BigDecimal("1000"))
            .reservedCash(BigDecimal.ZERO).holdings(Map.of()).build();

    private UserPersistencePort users;
    private InstrumentPersistencePort instruments;
    private OrderPersistencePort orders;
    private MarketDataPersistencePort marketData;
    private PlaceOrderContext placeOrderContext;
    private CalculatePositionsContext calculatePositionsContext;
    private CheckOrderRejectionContext checkOrderRejectionContext;
    private PlaceOrderUseCase useCase;

    private static PlaceOrderCommand marketBuy() {
        return PlaceOrderCommand.builder().instrumentId(ASSET).side(SideEnum.BUY).type(OrderTypeEnum.MARKET)
                .quantity(BigDecimal.TEN).build();
    }

    private static PlaceOrderCommand limitBuy() {
        return PlaceOrderCommand.builder().instrumentId(ASSET).side(SideEnum.BUY).type(OrderTypeEnum.LIMIT)
                .quantity(BigDecimal.TEN).limitPrice(CLOSE).build();
    }

    private static InstrumentModel instrument(String type) {
        return new InstrumentModel(ASSET, "AAA", "Activo A", type);
    }

    @BeforeEach
    void setUp() {
        users = mock(UserPersistencePort.class);
        instruments = mock(InstrumentPersistencePort.class);
        orders = mock(OrderPersistencePort.class);
        marketData = mock(MarketDataPersistencePort.class);
        placeOrderContext = mock(PlaceOrderContext.class);
        calculatePositionsContext = mock(CalculatePositionsContext.class);
        checkOrderRejectionContext = mock(CheckOrderRejectionContext.class);
        useCase = new PlaceOrderUseCase(users, instruments, orders, marketData, placeOrderContext,
                calculatePositionsContext, checkOrderRejectionContext, Clock.fixed(NOW, ZoneOffset.UTC));

        when(users.lockById(USER_ID)).thenReturn(Optional.of(UserModel.builder().id(USER_ID).build()));
        when(instruments.findById(ASSET)).thenReturn(Optional.of(instrument("ACCIONES")));
        when(instruments.findArsInstruments()).thenReturn(List.of(new InstrumentModel(66, "ARS", "Peso", "MONEDA")));
        when(marketData.findLatest(ASSET)).thenReturn(Optional.of(
                MarketDataModel.builder().instrumentId(ASSET).close(CLOSE).build()));
        when(orders.findByUserOrderedByDatetime(USER_ID)).thenReturn(List.of());
        when(calculatePositionsContext.apply(any())).thenReturn(POSITIONS);
        when(orders.insert(any())).thenAnswer(invocation -> {
            NewOrderModel order = invocation.getArgument(0);
            return OrderModel.builder().id(9).userId(order.userId()).instrumentId(order.instrumentId())
                    .side(order.side()).size(order.size()).price(order.price()).type(order.type())
                    .status(order.status()).datetime(order.datetime()).build();
        });
    }

    @Test
    @DisplayName("Persists a filled market order at the quoted price with the clock time")
    void givenAcceptedMarketOrder_whenExecuting_thenFilledOrderIsInserted() {
        // Given
        PlaceOrderCommand command = marketBuy();
        when(placeOrderContext.apply(command, CLOSE))
                .thenReturn(new PlaceOrderDecisionModel(BigDecimal.TEN, CLOSE, OrderStatusEnum.FILLED, null));

        // When
        PlaceOrderResultModel result = useCase.execute(USER_ID, command);

        // Then
        ArgumentCaptor<NewOrderModel> captor = ArgumentCaptor.forClass(NewOrderModel.class);
        verify(orders).insert(captor.capture());
        NewOrderModel inserted = captor.getValue();
        assertThat(inserted.status()).as("The order is filled").isEqualTo(OrderStatusEnum.FILLED);
        assertThat(inserted.price()).as("The price is the market close").isEqualByComparingTo("80");
        assertThat(inserted.size()).as("The size is the decided quantity").isEqualByComparingTo("10");
        assertThat(inserted.datetime()).as("The time comes from the clock").isEqualTo(NOW);
        assertThat(result.rejectionReason()).as("There is no rejection").isNull();
        assertThat(result.order().id()).as("The persisted order is returned").isEqualTo(9);
    }

    @Test
    @DisplayName("Persists a new limit order without querying the market")
    void givenAcceptedLimitOrder_whenExecuting_thenNewOrderIsInsertedWithoutQuote() {
        // Given
        PlaceOrderCommand command = limitBuy();
        when(placeOrderContext.apply(command, null))
                .thenReturn(new PlaceOrderDecisionModel(BigDecimal.TEN, CLOSE, OrderStatusEnum.NEW, null));

        // When
        PlaceOrderResultModel result = useCase.execute(USER_ID, command);

        // Then
        assertThat(result.order().status()).as("A limit order waits as new").isEqualTo(OrderStatusEnum.NEW);
        verify(marketData, never()).findLatest(anyInt());
    }

    @Test
    @DisplayName("Persists the order as rejected with the reason when it cannot be afforded")
    void givenRejectionReason_whenExecuting_thenOrderIsInsertedAsRejected() {
        // Given
        PlaceOrderCommand command = marketBuy();
        PlaceOrderDecisionModel decision = new PlaceOrderDecisionModel(BigDecimal.TEN, CLOSE,
                OrderStatusEnum.FILLED, null);
        when(placeOrderContext.apply(command, CLOSE)).thenReturn(decision);
        when(checkOrderRejectionContext.apply(command, decision, POSITIONS))
                .thenReturn(RejectionReasonEnum.INSUFFICIENT_FUNDS);

        // When
        PlaceOrderResultModel result = useCase.execute(USER_ID, command);

        // Then
        assertThat(result.order().status()).as("The order is rejected").isEqualTo(OrderStatusEnum.REJECTED);
        assertThat(result.rejectionReason()).as("The reason is reported")
                .isEqualTo(RejectionReasonEnum.INSUFFICIENT_FUNDS);
    }

    @Test
    @DisplayName("Fails when the user does not exist")
    void givenUnknownUser_whenExecuting_thenUserIsNotFound() {
        // Given
        when(users.lockById(USER_ID)).thenReturn(Optional.empty());

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, marketBuy()));

        // Then
        assertThat(failure).as("The user must exist").isInstanceOf(UserNotFoundException.class);
        verify(orders, never()).insert(any());
    }

    @Test
    @DisplayName("Fails when the instrument does not exist")
    void givenUnknownInstrument_whenExecuting_thenInstrumentIsNotFound() {
        // Given
        when(instruments.findById(ASSET)).thenReturn(Optional.empty());

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, marketBuy()));

        // Then
        assertThat(failure).as("The instrument must exist").isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    @DisplayName("Fails when the instrument is a currency")
    void givenCurrencyInstrument_whenExecuting_thenOrderIsInvalid() {
        // Given
        when(instruments.findById(ASSET)).thenReturn(Optional.of(instrument("MONEDA")));

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, marketBuy()));

        // Then
        assertThat(failure).as("Currencies cannot be traded").isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("Fails when there is not exactly one ARS instrument")
    void givenNoArsInstrument_whenExecuting_thenDataIsInconsistent() {
        // Given
        when(instruments.findArsInstruments()).thenReturn(List.of());

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, marketBuy()));

        // Then
        assertThat(failure).as("Exactly one ARS instrument is expected").isInstanceOf(InconsistentDataException.class);
    }

    @Test
    @DisplayName("Fails when a market order has no quote")
    void givenNoQuote_whenExecutingMarketOrder_thenMarketDataIsUnavailable() {
        // Given
        when(marketData.findLatest(ASSET)).thenReturn(Optional.empty());

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, marketBuy()));

        // Then
        assertThat(failure).as("A quote is required").isInstanceOf(MarketDataUnavailableException.class);
    }

    @Test
    @DisplayName("Fails when the quote close is not positive")
    void givenNonPositiveClose_whenExecutingMarketOrder_thenMarketDataIsUnavailable() {
        // Given
        when(marketData.findLatest(ASSET)).thenReturn(Optional.of(
                MarketDataModel.builder().instrumentId(ASSET).close(BigDecimal.ZERO).build()));

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, marketBuy()));

        // Then
        assertThat(failure).as("The close must be positive").isInstanceOf(MarketDataUnavailableException.class);
    }
}
