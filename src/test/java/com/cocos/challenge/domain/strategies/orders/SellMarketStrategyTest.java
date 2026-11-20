package com.cocos.challenge.domain.strategies.orders;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sell market strategy")
class SellMarketStrategyTest {
    private final SellMarketStrategy strategy = new SellMarketStrategy();

    @ParameterizedTest
    @CsvSource({"SELL,MARKET,true", "SELL,LIMIT,false", "BUY,MARKET,false", "BUY,LIMIT,false"})
    @DisplayName("Supports only sell market orders")
    void givenSideAndType_whenChecking_thenOnlySellMarketIsSupported(SideEnum side, OrderTypeEnum type,
                                                                     boolean expected) {
        // Given
        // A side and a type

        // When
        boolean supported = strategy.supports(side, type);

        // Then
        assertThat(supported).as("Only SELL + MARKET is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Fills at the market close")
    void givenQuantity_whenApplying_thenOrderIsFilledAtClose() {
        // Given
        PlaceOrderCommand command = PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.SELL)
                .type(OrderTypeEnum.MARKET).quantity(new BigDecimal("4")).build();

        // When
        PlaceOrderDecisionModel decision = strategy.apply(command, new BigDecimal("80"));

        // Then
        assertThat(decision.quantity()).as("The quantity is the requested one").isEqualByComparingTo("4");
        assertThat(decision.price()).as("The price is the market close").isEqualByComparingTo("80");
        assertThat(decision.status()).as("Market orders are filled").isEqualTo(OrderStatusEnum.FILLED);
    }
}
