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

@DisplayName("Filled buy strategy")
class FilledBuyStrategyTest {
    private final FilledBuyStrategy strategy = new FilledBuyStrategy();

    private static OrderModel order(String size, String price) {
        return OrderModel.builder().id(1).instrumentId(2).side(SideEnum.BUY).status(OrderStatusEnum.FILLED)
                .size(new BigDecimal(size)).price(new BigDecimal(price)).build();
    }

    private static PositionsModel state(Map<Integer, HoldingModel> holdings) {
        return PositionsModel.builder().settledCash(new BigDecimal("1000")).reservedCash(BigDecimal.ZERO)
                .holdings(holdings).build();
    }

    @ParameterizedTest
    @CsvSource({"FILLED,BUY,true", "FILLED,SELL,false", "NEW,BUY,false", "REJECTED,BUY,false", "CANCELLED,BUY,false"})
    @DisplayName("Supports only filled buy orders")
    void givenStatusAndSide_whenChecking_thenOnlyFilledBuyIsSupported(OrderStatusEnum status, SideEnum side,
                                                                      boolean expected) {
        // Given
        // A status and a side

        // When
        boolean supported = strategy.supports(status, side);

        // Then
        assertThat(supported).as("Only FILLED + BUY is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Spends cash and adds shares and cost to the existing holding")
    void givenExistingHolding_whenApplying_thenCashDropsAndHoldingGrows() {
        // Given
        HoldingModel holding = HoldingModel.builder().instrumentId(2).quantity(new BigDecimal("5"))
                .remainingCost(new BigDecimal("300")).reservedShares(BigDecimal.ONE).build();

        // When
        PositionsModel result = strategy.apply(order("10", "80"), state(Map.of(2, holding)));

        // Then
        HoldingModel updated = result.holdingOf(2);
        assertThat(result.settledCash()).as("800 is spent").isEqualByComparingTo("200");
        assertThat(updated.quantity()).as("Shares are added").isEqualByComparingTo("15");
        assertThat(updated.remainingCost()).as("Cost is added").isEqualByComparingTo("1100");
        assertThat(updated.reservedShares()).as("Reserved shares are kept").isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("Opens a holding when the instrument was not held")
    void givenNoHolding_whenApplying_thenHoldingIsOpened() {
        // Given
        PositionsModel state = state(Map.of());

        // When
        PositionsModel result = strategy.apply(order("2", "50"), state);

        // Then
        assertThat(result.holdingOf(2).quantity()).as("The shares are held").isEqualByComparingTo("2");
        assertThat(result.holdingOf(2).remainingCost()).as("The cost is tracked").isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Buying back a short position reduces its cost and can close it")
    void givenShortHolding_whenBuying_thenCostIsReducedOrCleared() {
        // Given
        HoldingModel shortHolding = HoldingModel.builder().instrumentId(2).quantity(new BigDecimal("-10"))
                .remainingCost(new BigDecimal("-800")).reservedShares(BigDecimal.ZERO).build();

        // When
        PositionsModel partial = strategy.apply(order("4", "80"), state(Map.of(2, shortHolding)));
        PositionsModel closed = strategy.apply(order("10", "80"), state(Map.of(2, shortHolding)));
        PositionsModel crossed = strategy.apply(order("12", "80"), state(Map.of(2, shortHolding)));

        // Then
        assertThat(partial.holdingOf(2).remainingCost()).as("Proportional short cost").isEqualByComparingTo("-480");
        assertThat(closed.holdingOf(2).remainingCost()).as("Closed position has no cost").isZero();
        assertThat(crossed.holdingOf(2).remainingCost()).as("New long cost").isEqualByComparingTo("160");
    }

    @Test
    @DisplayName("Fails on a persisted order with fractional shares")
    void givenFractionalShares_whenApplying_thenDataIsInconsistent() {
        // Given
        OrderModel order = order("1.5", "50");

        // When
        Throwable failure = catchThrowable(() -> strategy.apply(order, state(Map.of())));

        // Then
        assertThat(failure).as("Persisted trades must be valid").isInstanceOf(InconsistentDataException.class);
    }
}
