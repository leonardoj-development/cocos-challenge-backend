package com.cocos.challenge.domain.strategies;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.domain.strategies.orders.PlaceOrderStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DisplayName("Place order context")
class PlaceOrderContextTest {
    private static final PlaceOrderCommand COMMAND = PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.BUY)
            .type(OrderTypeEnum.MARKET).quantity(BigDecimal.TEN).build();
    private static final BigDecimal CLOSE = new BigDecimal("80");

    private static PlaceOrderStrategy strategy(boolean supports) {
        PlaceOrderStrategy strategy = mock(PlaceOrderStrategy.class);
        when(strategy.supports(SideEnum.BUY, OrderTypeEnum.MARKET)).thenReturn(supports);
        return strategy;
    }

    @Test
    @DisplayName("Delegates to the single strategy that supports the order")
    void givenSingleMatchingStrategy_whenApplying_thenDecisionComesFromIt() {
        // Given
        PlaceOrderStrategy matching = strategy(true);
        PlaceOrderStrategy other = strategy(false);
        PlaceOrderDecisionModel decision = new PlaceOrderDecisionModel(BigDecimal.TEN, CLOSE,
                OrderStatusEnum.FILLED, null);
        when(matching.apply(COMMAND, CLOSE)).thenReturn(decision);
        PlaceOrderContext context = new PlaceOrderContext(List.of(other, matching));

        // When
        PlaceOrderDecisionModel result = context.apply(COMMAND, CLOSE);

        // Then
        assertThat(result).as("The decision comes from the matching strategy").isSameAs(decision);
        verify(other, never()).apply(any(), any());
        verify(matching).apply(COMMAND, CLOSE);
    }

    @Test
    @DisplayName("Fails when no strategy supports the order")
    void givenNoMatchingStrategy_whenApplying_thenStateIsIllegal() {
        // Given
        PlaceOrderContext context = new PlaceOrderContext(List.of(strategy(false)));

        // When
        Throwable failure = catchThrowable(() -> context.apply(COMMAND, CLOSE));

        // Then
        assertThat(failure).as("No strategy is registered").isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No place-order strategy");
    }

    @Test
    @DisplayName("Fails when more than one strategy supports the order")
    void givenSeveralMatchingStrategies_whenApplying_thenStateIsIllegal() {
        // Given
        PlaceOrderContext context = new PlaceOrderContext(List.of(strategy(true), strategy(true)));

        // When
        Throwable failure = catchThrowable(() -> context.apply(COMMAND, CLOSE));

        // Then
        assertThat(failure).as("The selection is ambiguous").isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("More than one place-order strategy");
    }
}
