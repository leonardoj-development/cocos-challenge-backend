package com.cocos.challenge.domain.models;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Positions model")
class PositionsModelTest {
    private static final HoldingModel HOLDING = HoldingModel.builder().instrumentId(2).quantity(BigDecimal.TEN)
            .remainingCost(new BigDecimal("800")).reservedShares(BigDecimal.ONE).build();

    private static PositionsModel positions() {
        return PositionsModel.builder().settledCash(new BigDecimal("100")).reservedCash(new BigDecimal("20"))
                .holdings(Map.of(2, HOLDING)).build();
    }

    @Test
    @DisplayName("Returns the existing holding or an empty one")
    void givenHoldings_whenLookingUpInstrument_thenExistingOrEmptyHoldingIsReturned() {
        // Given
        PositionsModel positions = positions();

        // When
        HoldingModel existing = positions.holdingOf(2);
        HoldingModel missing = positions.holdingOf(9);

        // Then
        assertThat(existing).as("The held instrument is returned").isEqualTo(HOLDING);
        assertThat(missing.instrumentId()).as("The empty holding keeps the instrument").isEqualTo(9);
        assertThat(missing.quantity()).as("The empty holding has no quantity").isZero();
        assertThat(missing.remainingCost()).as("The empty holding has no cost").isZero();
        assertThat(missing.reservedShares()).as("The empty holding has no reserved shares").isZero();
    }

    @Test
    @DisplayName("Copies with cash, reserved cash and holdings without mutating the original")
    void givenPositions_whenUpdating_thenNewInstancesAreReturned() {
        // Given
        PositionsModel positions = positions();
        HoldingModel other = HoldingModel.builder().instrumentId(3).quantity(BigDecimal.ONE)
                .remainingCost(BigDecimal.ONE).reservedShares(BigDecimal.ZERO).build();

        // When
        PositionsModel withCash = positions.withCash(new BigDecimal("5"));
        PositionsModel withReserved = positions.withReservedCash(new BigDecimal("7"));
        PositionsModel withHolding = positions.withHolding(3, other);
        PositionsModel withBoth = positions.withCashAndHolding(new BigDecimal("9"), 3, other);

        // Then
        assertThat(withCash.settledCash()).as("The cash is replaced").isEqualByComparingTo("5");
        assertThat(withCash.reservedCash()).as("The reserved cash is kept").isEqualByComparingTo("20");
        assertThat(withReserved.reservedCash()).as("The reserved cash is replaced").isEqualByComparingTo("7");
        assertThat(withReserved.settledCash()).as("The cash is kept").isEqualByComparingTo("100");
        assertThat(withHolding.holdings()).as("The holding is added").containsEntry(3, other).containsEntry(2, HOLDING);
        assertThat(withBoth.settledCash()).as("The cash is replaced together with the holding").isEqualByComparingTo("9");
        assertThat(withBoth.holdings()).as("The holding is added together with the cash").containsEntry(3, other);
        assertThat(positions.holdings()).as("The original is not mutated").containsOnlyKeys(2);
        assertThat(positions.settledCash()).as("The original cash is not mutated").isEqualByComparingTo("100");
    }
}
