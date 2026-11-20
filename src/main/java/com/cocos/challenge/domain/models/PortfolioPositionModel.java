package com.cocos.challenge.domain.models;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PortfolioPositionModel(int instrumentId, String ticker, String name, BigDecimal quantity,
                                     BigDecimal marketPrice, BigDecimal marketValue,
                                     BigDecimal totalReturnPercent, BigDecimal dailyReturnPercent) {
}
