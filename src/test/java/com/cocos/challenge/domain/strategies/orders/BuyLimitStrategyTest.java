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

@DisplayName("Buy limit strategy")
class BuyLimitStrategyTest {
    private final BuyLimitStrategy strategy = new BuyLimitStrategy();

    @ParameterizedTest
    @CsvSource({"BUY,LIMIT,true", "BUY,MARKET,false", "SELL,LIMIT,false", "SELL,MARKET,false"})
    @DisplayName("Supports only buy limit orders")
    void givenSideAndType_whenChecking_thenOnlyBuyLimitIsSupported(SideEnum side, OrderTypeEnum type,
                                                                   boolean expected) {
        // Given
        // A side and a type

        // When
        boolean supported = strategy.supports(side, type);

        // Then
        assertThat(supported).as("Only BUY + LIMIT is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Leaves the order new at the limit price, ignoring the market close")
    void givenAmount_whenApplying_thenOrderIsNewAtLimitPrice() {
        // Given
        PlaceOrderCommand command = PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.BUY)
                .type(OrderTypeEnum.LIMIT).amount(new BigDecimal("1000")).limitPrice(new BigDecimal("100")).build();

        // When
        PlaceOrderDecisionModel decision = strategy.apply(command, null);

        // Then
        assertThat(decision.quantity()).as("1000 buys 10 shares at 100").isEqualByComparingTo("10");
        assertThat(decision.price()).as("The price is the limit price").isEqualByComparingTo("100");
        assertThat(decision.status()).as("Limit orders wait as new").isEqualTo(OrderStatusEnum.NEW);
    }
}
