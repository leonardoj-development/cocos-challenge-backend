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

@DisplayName("Filled sell strategy")
class FilledSellStrategyTest {
    private final FilledSellStrategy strategy = new FilledSellStrategy();

    private static OrderModel order(String size, String price) {
        return OrderModel.builder().id(1).instrumentId(2).side(SideEnum.SELL).status(OrderStatusEnum.FILLED)
                .size(new BigDecimal(size)).price(new BigDecimal(price)).build();
    }

    private static PositionsModel state() {
        HoldingModel holding = HoldingModel.builder().instrumentId(2).quantity(BigDecimal.TEN)
                .remainingCost(new BigDecimal("800")).reservedShares(BigDecimal.ONE).build();
        return PositionsModel.builder().settledCash(new BigDecimal("100")).reservedCash(BigDecimal.ZERO)
                .holdings(Map.of(2, holding)).build();
    }

    @ParameterizedTest
    @CsvSource({"FILLED,SELL,true", "FILLED,BUY,false", "NEW,SELL,false", "REJECTED,SELL,false", "CANCELLED,SELL,false"})
    @DisplayName("Supports only filled sell orders")
    void givenStatusAndSide_whenChecking_thenOnlyFilledSellIsSupported(OrderStatusEnum status, SideEnum side,
                                                                       boolean expected) {
        // Given
        // A status and a side

        // When
        boolean supported = strategy.supports(status, side);

        // Then
        assertThat(supported).as("Only FILLED + SELL is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Adds the proceeds and reduces shares with proportional cost")
    void givenPartialSale_whenApplying_thenCostIsProportional() {
        // Given
        OrderModel order = order("4", "90");

        // When
        PositionsModel result = strategy.apply(order, state());

        // Then
        HoldingModel updated = result.holdingOf(2);
        assertThat(result.settledCash()).as("360 is received").isEqualByComparingTo("460");
        assertThat(updated.quantity()).as("Shares are reduced").isEqualByComparingTo("6");
        assertThat(updated.remainingCost()).as("Cost keeps the average").isEqualByComparingTo("480");
        assertThat(updated.reservedShares()).as("Reserved shares are kept").isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("Clears the cost when everything is sold")
    void givenFullSale_whenApplying_thenCostIsZero() {
        // Given
        OrderModel order = order("10", "90");

        // When
        PositionsModel result = strategy.apply(order, state());

        // Then
        assertThat(result.holdingOf(2).quantity()).as("No shares remain").isZero();
        assertThat(result.holdingOf(2).remainingCost()).as("No cost remains").isZero();
    }

    @Test
    @DisplayName("Sells without prior shares and keeps a negative cost")
    void givenNoShares_whenSelling_thenPositionIsNegative() {
        // Given
        HoldingModel empty = HoldingModel.builder().instrumentId(2).quantity(BigDecimal.ZERO)
                .remainingCost(BigDecimal.ZERO).reservedShares(BigDecimal.ZERO).build();
        PositionsModel state = PositionsModel.builder().settledCash(BigDecimal.ZERO).reservedCash(BigDecimal.ZERO)
                .holdings(Map.of(2, empty)).build();

        // When
        PositionsModel result = strategy.apply(order("5", "90"), state);

        // Then
        assertThat(result.holdingOf(2).quantity()).as("Short position").isEqualByComparingTo("-5");
        assertThat(result.holdingOf(2).remainingCost()).as("Negative cost").isEqualByComparingTo("-450");
        assertThat(result.settledCash()).as("Proceeds are received").isEqualByComparingTo("450");
    }

    @Test
    @DisplayName("Sells more than the holding and goes negative with proportional cost")
    void givenLargerSale_whenApplying_thenPositionIsNegative() {
        // Given
        OrderModel order = order("15", "90");

        // When
        PositionsModel result = strategy.apply(order, state());

        // Then
        assertThat(result.holdingOf(2).quantity()).as("Short position").isEqualByComparingTo("-5");
        assertThat(result.holdingOf(2).remainingCost()).as("Cost keeps the average").isEqualByComparingTo("-400");
    }

    @Test
    @DisplayName("Fails on a persisted order with a missing price")
    void givenMissingPrice_whenApplying_thenDataIsInconsistent() {
        // Given
        OrderModel order = OrderModel.builder().id(1).instrumentId(2).side(SideEnum.SELL)
                .status(OrderStatusEnum.FILLED).size(BigDecimal.ONE).build();

        // When
        Throwable failure = catchThrowable(() -> strategy.apply(order, state()));

        // Then
        assertThat(failure).as("Persisted trades must be valid").isInstanceOf(InconsistentDataException.class);
    }
}
