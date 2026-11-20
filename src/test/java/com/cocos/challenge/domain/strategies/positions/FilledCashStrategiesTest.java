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

@DisplayName("Cash movement strategies")
class FilledCashStrategiesTest {
    private static PositionsModel state() {
        return PositionsModel.builder().settledCash(new BigDecimal("100")).reservedCash(BigDecimal.ZERO)
                .holdings(Map.of()).build();
    }

    private static OrderModel order(SideEnum side) {
        return OrderModel.builder().id(1).side(side).status(OrderStatusEnum.FILLED).size(new BigDecimal("30"))
                .build();
    }

    @ParameterizedTest
    @CsvSource({"FILLED,CASH_IN,true", "FILLED,CASH_OUT,false", "NEW,CASH_IN,false", "REJECTED,CASH_IN,false"})
    @DisplayName("Cash in supports only filled cash in orders")
    void givenStatusAndSide_whenCheckingCashIn_thenOnlyFilledCashInIsSupported(OrderStatusEnum status, SideEnum side,
                                                                               boolean expected) {
        // Given
        FilledCashInStrategy strategy = new FilledCashInStrategy();

        // When
        boolean supported = strategy.supports(status, side);

        // Then
        assertThat(supported).as("Only FILLED + CASH_IN is supported").isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"FILLED,CASH_OUT,true", "FILLED,CASH_IN,false", "NEW,CASH_OUT,false", "REJECTED,CASH_OUT,false"})
    @DisplayName("Cash out supports only filled cash out orders")
    void givenStatusAndSide_whenCheckingCashOut_thenOnlyFilledCashOutIsSupported(OrderStatusEnum status,
                                                                                 SideEnum side, boolean expected) {
        // Given
        FilledCashOutStrategy strategy = new FilledCashOutStrategy();

        // When
        boolean supported = strategy.supports(status, side);

        // Then
        assertThat(supported).as("Only FILLED + CASH_OUT is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Cash in adds the size to the settled cash")
    void givenCashIn_whenApplying_thenCashGrows() {
        // Given
        FilledCashInStrategy strategy = new FilledCashInStrategy();

        // When
        PositionsModel result = strategy.apply(order(SideEnum.CASH_IN), state());

        // Then
        assertThat(result.settledCash()).as("30 is deposited").isEqualByComparingTo("130");
    }

    @Test
    @DisplayName("Cash out subtracts the size from the settled cash")
    void givenCashOut_whenApplying_thenCashDrops() {
        // Given
        FilledCashOutStrategy strategy = new FilledCashOutStrategy();

        // When
        PositionsModel result = strategy.apply(order(SideEnum.CASH_OUT), state());

        // Then
        assertThat(result.settledCash()).as("30 is withdrawn").isEqualByComparingTo("70");
    }
}
