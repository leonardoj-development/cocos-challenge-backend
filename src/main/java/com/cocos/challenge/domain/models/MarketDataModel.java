package com.cocos.challenge.domain.models;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record MarketDataModel(int id, int instrumentId, BigDecimal close, BigDecimal previousClose, LocalDate date) {
}
