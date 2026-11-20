package com.cocos.challenge.domain.models;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Builder
public record PositionsModel(BigDecimal settledCash, BigDecimal reservedCash, Map<Integer, HoldingModel> holdings) {

    public HoldingModel holdingOf(int instrumentId) {
        return holdings.getOrDefault(instrumentId, HoldingModel.builder()
                .instrumentId(instrumentId)
                .quantity(BigDecimal.ZERO)
                .remainingCost(BigDecimal.ZERO)
                .reservedShares(BigDecimal.ZERO)
                .build());
    }

    public PositionsModel withCash(BigDecimal cash) {
        return PositionsModel.builder().settledCash(cash).reservedCash(reservedCash).holdings(holdings).build();
    }

    public PositionsModel withReservedCash(BigDecimal updatedReservedCash) {
        return PositionsModel.builder().settledCash(settledCash).reservedCash(updatedReservedCash).holdings(holdings).build();
    }

    public PositionsModel withHolding(int instrumentId, HoldingModel holding) {
        Map<Integer, HoldingModel> updated = new HashMap<>(holdings);
        updated.put(instrumentId, holding);
        return PositionsModel.builder().settledCash(settledCash).reservedCash(reservedCash).holdings(updated).build();
    }

    public PositionsModel withCashAndHolding(BigDecimal cash, int instrumentId, HoldingModel holding) {
        Map<Integer, HoldingModel> updated = new HashMap<>(holdings);
        updated.put(instrumentId, holding);
        return PositionsModel.builder().settledCash(cash).reservedCash(reservedCash).holdings(updated).build();
    }
}
