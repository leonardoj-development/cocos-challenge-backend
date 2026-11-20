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

@DisplayName("Sell limit strategy")
class SellLimitStrategyTest {
    private final SellLimitStrategy strategy = new SellLimitStrategy();

    @ParameterizedTest
    @CsvSource({"SELL,LIMIT,true", "SELL,MARKET,false", "BUY,LIMIT,false", "BUY,MARKET,false"})
    @DisplayName("Supports only sell limit orders")
    void givenSideAndType_whenChecking_thenOnlySellLimitIsSupported(SideEnum side, OrderTypeEnum type,
                                                                    boolean expected) {
        // Given
        // A side and a type

        // When
        boolean supported = strategy.supports(side, type);

        // Then
        assertThat(supported).as("Only SELL + LIMIT is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Leaves the order new at the limit price, ignoring the market close")
    void givenQuantity_whenApplying_thenOrderIsNewAtLimitPrice() {
        // Given
        PlaceOrderCommand command = PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.SELL)
                .type(OrderTypeEnum.LIMIT).quantity(new BigDecimal("3")).limitPrice(new BigDecimal("90")).build();

        // When
        PlaceOrderDecisionModel decision = strategy.apply(command, null);

        // Then
        assertThat(decision.quantity()).as("The quantity is the requested one").isEqualByComparingTo("3");
        assertThat(decision.price()).as("The price is the limit price").isEqualByComparingTo("90");
        assertThat(decision.status()).as("Limit orders wait as new").isEqualTo(OrderStatusEnum.NEW);
    }
}
