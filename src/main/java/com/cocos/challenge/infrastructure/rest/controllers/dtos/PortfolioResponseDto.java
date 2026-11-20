package com.cocos.challenge.infrastructure.rest.controllers.dtos;

import com.cocos.challenge.infrastructure.rest.controllers.serializers.AmountSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class PortfolioResponseDto {
    private final int userId;
    private final String currency;
    @JsonSerialize(using = AmountSerializer.class)
    private final BigDecimal totalValue;
    @JsonSerialize(using = AmountSerializer.class)
    private final BigDecimal availableCash;
    private final List<PositionResponseDto> positions;
}
