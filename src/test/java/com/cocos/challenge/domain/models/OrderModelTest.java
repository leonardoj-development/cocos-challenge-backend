package com.cocos.challenge.domain.models;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("Order model share quantity rules")
class OrderModelTest {
    @Test
    @DisplayName("Recognizes whole share quantities, including values with trailing zeros")
    void givenShareQuantities_whenCheckingWholeShares_thenOnlyIntegralValuesPass() {
        assertThat(OrderModel.isWholeShareQuantity(new BigDecimal("10.000"))).isTrue();
        assertThat(OrderModel.isWholeShareQuantity(new BigDecimal("1.5"))).isFalse();
        assertThat(OrderModel.isWholeShareQuantity(new BigDecimal("100e2147483647"))).isTrue();
    }

    private static OrderModel order(String size, String price) {
        return OrderModel.builder().id(7).size(new BigDecimal(size)).price(price == null ? null : new BigDecimal(price))
                .build();
    }

    @Test
    @DisplayName("Accepts a trade with whole shares and a positive price")
    void givenWholeSharesAndPositivePrice_whenValidatingTrade_thenNothingIsThrown() {
        // Given
        OrderModel order = order("10", "80");

        // When
        Throwable failure = catchThrowable(order::requireValidTrade);

        // Then
        assertThat(failure).as("A valid trade passes").isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"1.5,80", "10,0", "10,-1", "10,"})
    @DisplayName("Rejects fractional shares and missing or non-positive prices as inconsistent data")
    void givenInvalidTrade_whenValidatingTrade_thenDataIsInconsistent(String size, String price) {
        // Given
        OrderModel order = order(size, price);

        // When
        Throwable failure = catchThrowable(order::requireValidTrade);

        // Then
        assertThat(failure).as("An invalid persisted trade is inconsistent data")
                .isInstanceOf(InconsistentDataException.class);
    }
}
