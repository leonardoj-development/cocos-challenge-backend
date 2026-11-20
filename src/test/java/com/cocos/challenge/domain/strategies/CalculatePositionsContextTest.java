package com.cocos.challenge.domain.strategies;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.models.HoldingModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.strategies.positions.CalculatePositionsStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Calculate positions context")
class CalculatePositionsContextTest {
    private static OrderModel order(int id) {
        return OrderModel.builder().id(id).side(SideEnum.BUY).status(OrderStatusEnum.FILLED).build();
    }

    private static CalculatePositionsStrategy strategy(boolean supports) {
        CalculatePositionsStrategy strategy = mock(CalculatePositionsStrategy.class);
        when(strategy.supports(OrderStatusEnum.FILLED, SideEnum.BUY)).thenReturn(supports);
        return strategy;
    }

    private static HoldingModel holding(int instrumentId) {
        return HoldingModel.builder().instrumentId(instrumentId).quantity(BigDecimal.ONE)
                .remainingCost(BigDecimal.ONE).reservedShares(BigDecimal.ZERO).build();
    }

    @Test
    @DisplayName("Starts from empty positions when there are no orders")
    void givenNoOrders_whenApplying_thenPositionsAreEmpty() {
        // Given
        CalculatePositionsContext context = new CalculatePositionsContext(List.of());

        // When
        PositionsModel positions = context.apply(List.of());

        // Then
        assertThat(positions.settledCash()).as("No cash").isZero();
        assertThat(positions.reservedCash()).as("No reserved cash").isZero();
        assertThat(positions.holdings()).as("No holdings").isEmpty();
    }

    @Test
    @DisplayName("Folds the orders in sequence and returns the holdings sorted by instrument")
    void givenOrders_whenApplying_thenStateIsFoldedAndSorted() {
        // Given
        CalculatePositionsStrategy matching = strategy(true);
        when(matching.apply(any(), any())).thenAnswer(invocation -> {
            OrderModel order = invocation.getArgument(0);
            PositionsModel state = invocation.getArgument(1);
            return state.withCashAndHolding(state.settledCash().add(BigDecimal.TEN), 10 - order.id(),
                    holding(10 - order.id()));
        });
        CalculatePositionsContext context = new CalculatePositionsContext(List.of(strategy(false), matching));

        // When
        PositionsModel positions = context.apply(List.of(order(1), order(2)));

        // Then
        assertThat(positions.settledCash()).as("Each order is folded into the state").isEqualByComparingTo("20");
        assertThat(positions.holdings().keySet()).as("Holdings are sorted by instrument").containsExactly(8, 9);
        Throwable failure = catchThrowable(() -> positions.holdings().put(1, holding(1)));
        assertThat(failure).as("The holdings are unmodifiable").isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Fails when an order leaves the settled cash negative")
    void givenOrderMakingCashNegative_whenApplying_thenDataIsInconsistent() {
        // Given
        CalculatePositionsStrategy matching = strategy(true);
        when(matching.apply(any(), any())).thenAnswer(invocation ->
                ((PositionsModel) invocation.getArgument(1)).withCash(new BigDecimal("-1")));
        CalculatePositionsContext context = new CalculatePositionsContext(List.of(matching));

        // When
        Throwable failure = catchThrowable(() -> context.apply(List.of(order(5))));

        // Then
        assertThat(failure).as("The cash invariant is enforced").isInstanceOf(InconsistentDataException.class)
                .hasMessageContaining("5");
    }

    @Test
    @DisplayName("Fails when no strategy supports the order")
    void givenNoMatchingStrategy_whenApplying_thenStateIsIllegal() {
        // Given
        CalculatePositionsContext context = new CalculatePositionsContext(List.of(strategy(false)));

        // When
        Throwable failure = catchThrowable(() -> context.apply(List.of(order(1))));

        // Then
        assertThat(failure).as("No strategy is registered").isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No positions strategy");
    }

    @Test
    @DisplayName("Fails when more than one strategy supports the order")
    void givenSeveralMatchingStrategies_whenApplying_thenStateIsIllegal() {
        // Given
        CalculatePositionsContext context = new CalculatePositionsContext(List.of(strategy(true), strategy(true)));

        // When
        Throwable failure = catchThrowable(() -> context.apply(List.of(order(1))));

        // Then
        assertThat(failure).as("The selection is ambiguous").isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("More than one positions strategy");
    }
}
