package com.cocos.challenge.domain.strategies.rejections;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Buy rejection strategy")
class BuyRejectionStrategyTest {
    private final BuyRejectionStrategy strategy = new BuyRejectionStrategy();

    private static PositionsModel positions(String settled, String reserved) {
        return PositionsModel.builder().settledCash(new BigDecimal(settled)).reservedCash(new BigDecimal(reserved))
                .holdings(Map.of()).build();
    }

    private static PlaceOrderDecisionModel decision(String quantity, String price) {
        return new PlaceOrderDecisionModel(new BigDecimal(quantity), new BigDecimal(price), OrderStatusEnum.FILLED,
                null);
    }

    @ParameterizedTest
    @CsvSource({"BUY,true", "SELL,false", "CASH_IN,false", "CASH_OUT,false"})
    @DisplayName("Supports only buy orders")
    void givenSide_whenChecking_thenOnlyBuyIsSupported(SideEnum side, boolean expected) {
        // Given
        // A side

        // When
        boolean supported = strategy.supports(side);

        // Then
        assertThat(supported).as("Only BUY is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Accepts an order that costs exactly the available cash")
    void givenCostEqualToAvailableCash_whenApplying_thenNoRejection() {
        // Given
        PositionsModel positions = positions("1000", "200");

        // When
        RejectionReasonEnum reason = strategy.apply(null, decision("10", "80"), positions);

        // Then
        assertThat(reason).as("800 is exactly the available cash").isNull();
    }

    @Test
    @DisplayName("Rejects an order that costs more than the available cash, discounting reservations")
    void givenCostAboveAvailableCash_whenApplying_thenFundsAreInsufficient() {
        // Given
        PositionsModel positions = positions("1000", "201");

        // When
        RejectionReasonEnum reason = strategy.apply(null, decision("10", "80"), positions);

        // Then
        assertThat(reason).as("The reservation leaves 799").isEqualTo(RejectionReasonEnum.INSUFFICIENT_FUNDS);
    }
}
