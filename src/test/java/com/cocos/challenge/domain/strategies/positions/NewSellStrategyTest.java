package com.cocos.challenge.domain.strategies.positions;

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

@DisplayName("New sell strategy")
class NewSellStrategyTest {
    private final NewSellStrategy strategy = new NewSellStrategy();

    @ParameterizedTest
    @CsvSource({"NEW,SELL,true", "NEW,BUY,false", "FILLED,SELL,false", "REJECTED,SELL,false", "CANCELLED,SELL,false"})
    @DisplayName("Supports only new sell orders")
    void givenStatusAndSide_whenChecking_thenOnlyNewSellIsSupported(OrderStatusEnum status, SideEnum side,
                                                                    boolean expected) {
        // Given
        // A status and a side

        // When
        boolean supported = strategy.supports(status, side);

        // Then
        assertThat(supported).as("Only NEW + SELL is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Reserves shares without changing quantity or cost")
    void givenNewSell_whenApplying_thenSharesAreReserved() {
        // Given
        HoldingModel holding = HoldingModel.builder().instrumentId(2).quantity(BigDecimal.TEN)
                .remainingCost(new BigDecimal("800")).reservedShares(BigDecimal.ONE).build();
        PositionsModel state = PositionsModel.builder().settledCash(BigDecimal.ZERO).reservedCash(BigDecimal.ZERO)
                .holdings(Map.of(2, holding)).build();
        OrderModel order = OrderModel.builder().id(1).instrumentId(2).side(SideEnum.SELL).status(OrderStatusEnum.NEW)
                .size(new BigDecimal("3")).price(new BigDecimal("90")).build();

        // When
        PositionsModel result = strategy.apply(order, state);

        // Then
        HoldingModel updated = result.holdingOf(2);
        assertThat(updated.reservedShares()).as("3 shares are added to the reservation").isEqualByComparingTo("4");
        assertThat(updated.quantity()).as("The quantity is untouched").isEqualByComparingTo("10");
        assertThat(updated.remainingCost()).as("The cost is untouched").isEqualByComparingTo("800");
    }
}
