package com.cocos.challenge.domain.models;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record PortfolioModel(int userId, String currency, BigDecimal totalValue, BigDecimal availableCash,
                             List<PortfolioPositionModel> positions) {
}
