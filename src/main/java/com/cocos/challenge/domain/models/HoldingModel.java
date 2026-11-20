package com.cocos.challenge.domain.models;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record HoldingModel(int instrumentId, BigDecimal quantity, BigDecimal remainingCost, BigDecimal reservedShares) {
}
