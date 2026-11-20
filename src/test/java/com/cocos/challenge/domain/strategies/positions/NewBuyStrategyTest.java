package com.cocos.challenge.domain.strategies.positions;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.models.HoldingModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("New buy strategy")
class NewBuyStrategyTest {
    private final NewBuyStrategy strategy = new NewBuyStrategy();

    private static PositionsModel state() {
        return PositionsModel.builder().settledCash(new BigDecimal("1000")).reservedCash(new BigDecimal("100"))
                .holdings(Map.of()).build();
    }

    @ParameterizedTest
    @CsvSource({"NEW,BUY,true", "NEW,SELL,false", "FILLED,BUY,false", "REJECTED,BUY,false", "CANCELLED,BUY,false"})
    @DisplayName("Supports only new buy orders")
    void givenStatusAndSide_whenChecking_thenOnlyNewBuyIsSupported(OrderStatusEnum status, SideEnum side,
                                                                   boolean expected) {
        // Given
        // A status and a side

        // When
        boolean supported = strategy.supports(status, side);

        // Then
        assertThat(supported).as("Only NEW + BUY is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Reserves the cost of the order without spending cash")
    void givenNewBuy_whenApplying_thenCashIsReserved() {
        // Given
        OrderModel order = OrderModel.builder().id(1).instrumentId(2).side(SideEnum.BUY).status(OrderStatusEnum.NEW)
                .size(new BigDecimal("5")).price(new BigDecimal("100")).build();

        // When
        PositionsModel result = strategy.apply(order, state());

        // Then
        assertThat(result.reservedCash()).as("500 is added to the reservation").isEqualByComparingTo("600");
        assertThat(result.settledCash()).as("Settled cash is untouched").isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Fails on a persisted order without a positive price")
    void givenMissingPrice_whenApplying_thenDataIsInconsistent() {
        // Given
        OrderModel order = OrderModel.builder().id(1).instrumentId(2).side(SideEnum.BUY).status(OrderStatusEnum.NEW)
                .size(BigDecimal.ONE).build();

        // When
        Throwable failure = catchThrowable(() -> strategy.apply(order, state()));

        // Then
        assertThat(failure).as("Persisted trades must be valid").isInstanceOf(InconsistentDataException.class);
    }
}
