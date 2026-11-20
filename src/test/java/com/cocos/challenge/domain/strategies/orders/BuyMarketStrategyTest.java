package com.cocos.challenge.domain.strategies.orders;

import com.cocos.challenge.domain.exceptions.InvalidOrderException;
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
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("Buy market strategy")
class BuyMarketStrategyTest {
    private static final BigDecimal CLOSE = new BigDecimal("80");

    private final BuyMarketStrategy strategy = new BuyMarketStrategy();

    private static PlaceOrderCommand.PlaceOrderCommandBuilder command() {
        return PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.BUY).type(OrderTypeEnum.MARKET);
    }

    @ParameterizedTest
    @CsvSource({"BUY,MARKET,true", "BUY,LIMIT,false", "SELL,MARKET,false", "SELL,LIMIT,false"})
    @DisplayName("Supports only buy market orders")
    void givenSideAndType_whenChecking_thenOnlyBuyMarketIsSupported(SideEnum side, OrderTypeEnum type,
                                                                    boolean expected) {
        // Given
        // A side and a type

        // When
        boolean supported = strategy.supports(side, type);

        // Then
        assertThat(supported).as("Only BUY + MARKET is supported").isEqualTo(expected);
    }

    @Test
    @DisplayName("Fills at the market close with the requested quantity")
    void givenQuantity_whenApplying_thenOrderIsFilledAtClose() {
        // Given
        PlaceOrderCommand command = command().quantity(BigDecimal.TEN).build();

        // When
        PlaceOrderDecisionModel decision = strategy.apply(command, CLOSE);

        // Then
        assertThat(decision.quantity()).as("The quantity is the requested one").isEqualByComparingTo("10");
        assertThat(decision.price()).as("The price is the market close").isEqualByComparingTo("80");
        assertThat(decision.status()).as("Market orders are filled").isEqualTo(OrderStatusEnum.FILLED);
        assertThat(decision.rejection()).as("There is no rejection yet").isNull();
    }

    @Test
    @DisplayName("Buys whole shares with the amount and leaves the remainder")
    void givenAmount_whenApplying_thenWholeSharesAreBought() {
        // Given
        PlaceOrderCommand command = command().amount(new BigDecimal("1000")).build();

        // When
        PlaceOrderDecisionModel decision = strategy.apply(command, CLOSE);

        // Then
        assertThat(decision.quantity()).as("1000 buys 12 shares at 80").isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("Rejects an amount below the price of one share")
    void givenAmountBelowPrice_whenApplying_thenOrderIsInvalid() {
        // Given
        PlaceOrderCommand command = command().amount(new BigDecimal("79.99")).build();

        // When
        Throwable failure = catchThrowable(() -> strategy.apply(command, CLOSE));

        // Then
        assertThat(failure).as("The amount cannot buy a share").isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("Rejects an amount with a non-positive price")
    void givenNonPositivePrice_whenApplying_thenOrderIsInvalid() {
        // Given
        PlaceOrderCommand command = command().amount(new BigDecimal("100")).build();

        // When
        Throwable failure = catchThrowable(() -> strategy.apply(command, BigDecimal.ZERO));

        // Then
        assertThat(failure).as("The price must be positive").isInstanceOf(InvalidOrderException.class);
    }
}
