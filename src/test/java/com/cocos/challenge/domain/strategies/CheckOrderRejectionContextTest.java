package com.cocos.challenge.domain.strategies;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.domain.strategies.rejections.OrderRejectionStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Check order rejection context")
class CheckOrderRejectionContextTest {
    private static final PlaceOrderCommand COMMAND = PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.BUY)
            .type(OrderTypeEnum.MARKET).quantity(BigDecimal.TEN).build();
    private static final PlaceOrderDecisionModel DECISION = new PlaceOrderDecisionModel(BigDecimal.TEN,
            new BigDecimal("80"), OrderStatusEnum.FILLED, null);
    private static final PositionsModel POSITIONS = PositionsModel.builder().settledCash(BigDecimal.ZERO)
            .reservedCash(BigDecimal.ZERO).holdings(Map.of()).build();

    private static OrderRejectionStrategy strategy(boolean supports) {
        OrderRejectionStrategy strategy = mock(OrderRejectionStrategy.class);
        when(strategy.supports(SideEnum.BUY)).thenReturn(supports);
        return strategy;
    }

    @Test
    @DisplayName("Returns the reason of the single strategy that supports the side")
    void givenSingleMatchingStrategy_whenApplying_thenReasonComesFromIt() {
        // Given
        OrderRejectionStrategy matching = strategy(true);
        when(matching.apply(COMMAND, DECISION, POSITIONS)).thenReturn(RejectionReasonEnum.INSUFFICIENT_FUNDS);
        CheckOrderRejectionContext context = new CheckOrderRejectionContext(List.of(strategy(false), matching));

        // When
        RejectionReasonEnum reason = context.apply(COMMAND, DECISION, POSITIONS);

        // Then
        assertThat(reason).as("The reason comes from the matching strategy")
                .isEqualTo(RejectionReasonEnum.INSUFFICIENT_FUNDS);
    }

    @Test
    @DisplayName("Fails when no strategy supports the side")
    void givenNoMatchingStrategy_whenApplying_thenStateIsIllegal() {
        // Given
        CheckOrderRejectionContext context = new CheckOrderRejectionContext(List.of(strategy(false)));

        // When
        Throwable failure = catchThrowable(() -> context.apply(COMMAND, DECISION, POSITIONS));

        // Then
        assertThat(failure).as("No strategy is registered").isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No order-rejection strategy");
    }

    @Test
    @DisplayName("Fails when more than one strategy supports the side")
    void givenSeveralMatchingStrategies_whenApplying_thenStateIsIllegal() {
        // Given
        CheckOrderRejectionContext context = new CheckOrderRejectionContext(List.of(strategy(true), strategy(true)));

        // When
        Throwable failure = catchThrowable(() -> context.apply(COMMAND, DECISION, POSITIONS));

        // Then
        assertThat(failure).as("The selection is ambiguous").isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("More than one order-rejection strategy");
    }
}
