package com.cocos.challenge.domain.strategies.rejections;

import com.cocos.challenge.domain.models.HoldingModel;
import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sell rejection strategy")
class SellRejectionStrategyTest {
    private static final PlaceOrderCommand COMMAND = PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.SELL)
            .type(OrderTypeEnum.MARKET).quantity(BigDecimal.TEN).build();

    private final SellRejectionStrategy strategy = new SellRejectionStrategy();

    private static PositionsModel positions(String quantity, String reserved) {
        HoldingModel holding = HoldingModel.builder().instrumentId(2).quantity(new BigDecimal(quantity))
                .remainingCost(new BigDecimal("800")).reservedShares(new BigDecimal(reserved)).build();
        return PositionsModel.builder().settledCash(BigDecimal.ZERO).reservedCash(BigDecimal.ZERO)
                .holdings(Map.of(2, holding)).build();
    }

    private static PlaceOrderDecisionModel decision(String quantity) {
        return new PlaceOrderDecisionModel(new BigDecimal(quantity), new BigDecimal("80"), OrderStatusEnum.FILLED,
                null);
    }

    @ParameterizedTest
    @CsvSource({"SELL,true", "BUY,false", "CASH_IN,false", "CASH_OUT,false"})
    @DisplayName("Supports only sell orders")
    void givenSide_whenChecking_thenOnlySellIsSupported(SideEnum side, boolean expected) {
        // Given
        // A side

        // When
        boolean supported = strategy.supports(side);

        // Then
        assertThat(supported).as("Only SELL is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Accepts selling exactly the available shares")
    void givenQuantityEqualToAvailableShares_whenApplying_thenNoRejection() {
        // Given
        PositionsModel positions = positions("12", "2");

        // When
        RejectionReasonEnum reason = strategy.apply(COMMAND, decision("10"), positions);

        // Then
        assertThat(reason).as("10 is exactly the available shares").isNull();
    }

    @Test
    @DisplayName("Rejects selling more than the available shares, discounting reservations")
    void givenQuantityAboveAvailableShares_whenApplying_thenSharesAreInsufficient() {
        // Given
        PositionsModel positions = positions("12", "3");

        // When
        RejectionReasonEnum reason = strategy.apply(COMMAND, decision("10"), positions);

        // Then
        assertThat(reason).as("The reservation leaves 9 shares").isEqualTo(RejectionReasonEnum.INSUFFICIENT_SHARES);
    }

    @Test
    @DisplayName("Rejects selling an instrument that is not held")
    void givenNoHolding_whenApplying_thenSharesAreInsufficient() {
        // Given
        PositionsModel positions = PositionsModel.builder().settledCash(BigDecimal.ZERO)
                .reservedCash(BigDecimal.ZERO).holdings(Map.of()).build();

        // When
        RejectionReasonEnum reason = strategy.apply(COMMAND, decision("1"), positions);

        // Then
        assertThat(reason).as("There is nothing to sell").isEqualTo(RejectionReasonEnum.INSUFFICIENT_SHARES);
    }
}
