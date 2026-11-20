package com.cocos.challenge.domain.ports.in.commands;

import com.cocos.challenge.domain.exceptions.InvalidRequestException;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("Place order amount precision")
class PlaceOrderCommandTest {
    @Test
    @DisplayName("Accepts amounts within the input precision and rejects excessive magnitude or scale")
    void givenAmountsWithVariousScalesAndMagnitudes_whenCheckingAcceptedPrecision_thenOnlySupportedValuesPass() {
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(
                new BigDecimal("9999999999999999.12345678"))).isTrue();
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(new BigDecimal("1.123456789"))).isFalse();
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(new BigDecimal("10000000000000000"))).isFalse();
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(new BigDecimal("1e2147483647"))).isFalse();
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(new BigDecimal("100e2147483647"))).isFalse();
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(new BigDecimal("1e-2147483647"))).isFalse();
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(new BigDecimal("1e3"))).isTrue();
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(new BigDecimal("1.10000000000"))).isTrue();
        assertThat(PlaceOrderCommand.isAmountWithinAcceptedPrecision(BigDecimal.ZERO)).isTrue();
    }

    private static PlaceOrderCommand.PlaceOrderCommandBuilder validMarketBuy() {
        return PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.BUY).type(OrderTypeEnum.MARKET)
                .quantity(BigDecimal.TEN);
    }

    @Test
    @DisplayName("Builds valid market and limit commands")
    void givenValidInputs_whenBuilding_thenCommandsAreCreated() {
        // Given
        PlaceOrderCommand.PlaceOrderCommandBuilder market = validMarketBuy();
        PlaceOrderCommand.PlaceOrderCommandBuilder limit = PlaceOrderCommand.builder().instrumentId(2)
                .side(SideEnum.SELL).type(OrderTypeEnum.LIMIT).amount(new BigDecimal("100.5"))
                .limitPrice(new BigDecimal("10.5"));

        // When
        PlaceOrderCommand marketCommand = market.build();
        PlaceOrderCommand limitCommand = limit.build();

        // Then
        assertThat(marketCommand.quantity()).as("The market quantity is kept").isEqualByComparingTo("10");
        assertThat(limitCommand.limitPrice()).as("The limit price is kept").isEqualByComparingTo("10.5");
    }

    static Stream<Arguments> invalidCommands() {
        return Stream.of(
                Arguments.of("missing side", validMarketBuy().side(null)),
                Arguments.of("missing type", validMarketBuy().type(null)),
                Arguments.of("non positive instrument", validMarketBuy().instrumentId(0)),
                Arguments.of("cash side", validMarketBuy().side(SideEnum.CASH_IN)),
                Arguments.of("neither quantity nor amount", validMarketBuy().quantity(null)),
                Arguments.of("both quantity and amount", validMarketBuy().amount(BigDecimal.TEN)),
                Arguments.of("zero quantity", validMarketBuy().quantity(BigDecimal.ZERO)),
                Arguments.of("fractional quantity", validMarketBuy().quantity(new BigDecimal("1.5"))),
                Arguments.of("zero amount", validMarketBuy().quantity(null).amount(BigDecimal.ZERO)),
                Arguments.of("amount with excessive scale",
                        validMarketBuy().quantity(null).amount(new BigDecimal("1.123456789"))),
                Arguments.of("limit without price", validMarketBuy().type(OrderTypeEnum.LIMIT)),
                Arguments.of("limit with zero price",
                        validMarketBuy().type(OrderTypeEnum.LIMIT).limitPrice(BigDecimal.ZERO)),
                Arguments.of("market with price", validMarketBuy().limitPrice(BigDecimal.ONE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCommands")
    @DisplayName("Rejects commands that break an invariant")
    void givenInvalidInputs_whenBuilding_thenRequestIsInvalid(String scenario,
                                                              PlaceOrderCommand.PlaceOrderCommandBuilder builder) {
        // Given
        // The builder holds the invalid inputs

        // When
        Throwable failure = catchThrowable(builder::build);

        // Then
        assertThat(failure).as("The invariant is enforced: " + scenario).isInstanceOf(InvalidRequestException.class);
    }
}
