package com.cocos.challenge.domain.strategies.positions;

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

@DisplayName("Rejected and cancelled strategies")
class InertStrategiesTest {
    private static PositionsModel state() {
        return PositionsModel.builder().settledCash(new BigDecimal("100")).reservedCash(BigDecimal.ONE)
                .holdings(Map.of()).build();
    }

    @ParameterizedTest
    @CsvSource({"REJECTED,BUY,true", "REJECTED,SELL,true", "NEW,BUY,false", "CANCELLED,BUY,false"})
    @DisplayName("Rejected supports every side of a rejected order")
    void givenStatusAndSide_whenCheckingRejected_thenOnlyRejectedIsSupported(OrderStatusEnum status, SideEnum side,
                                                                             boolean expected) {
        // Given
        RejectedStrategy strategy = new RejectedStrategy();

        // When
        boolean supported = strategy.supports(status, side);

        // Then
        assertThat(supported).as("Only REJECTED orders are supported").isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"CANCELLED,BUY,true", "CANCELLED,SELL,true", "NEW,BUY,false", "REJECTED,BUY,false"})
    @DisplayName("Cancelled supports every side of a cancelled order")
    void givenStatusAndSide_whenCheckingCancelled_thenOnlyCancelledIsSupported(OrderStatusEnum status, SideEnum side,
                                                                               boolean expected) {
        // Given
        CancelledStrategy strategy = new CancelledStrategy();

        // When
        boolean supported = strategy.supports(status, side);

        // Then
        assertThat(supported).as("Only CANCELLED orders are supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Rejected and cancelled orders leave the positions untouched")
    void givenRejectedAndCancelledOrders_whenApplying_thenStateIsUnchanged() {
        // Given
        PositionsModel state = state();
        OrderModel order = OrderModel.builder().id(1).side(SideEnum.BUY).build();

        // When
        PositionsModel afterRejected = new RejectedStrategy().apply(order, state);
        PositionsModel afterCancelled = new CancelledStrategy().apply(order, state);

        // Then
        assertThat(afterRejected).as("A rejected order changes nothing").isSameAs(state);
        assertThat(afterCancelled).as("A cancelled order changes nothing").isSameAs(state);
    }
}
