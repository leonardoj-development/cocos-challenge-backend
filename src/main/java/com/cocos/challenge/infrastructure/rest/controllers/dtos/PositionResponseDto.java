package com.cocos.challenge.infrastructure.rest.controllers.dtos;

import com.cocos.challenge.infrastructure.rest.controllers.serializers.AmountSerializer;
import com.cocos.challenge.infrastructure.rest.controllers.serializers.PercentSerializer;
import com.cocos.challenge.infrastructure.rest.controllers.serializers.QuantitySerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PositionResponseDto {
    private final int instrumentId;
    private final String ticker;
    private final String name;
    @JsonSerialize(using = QuantitySerializer.class)
    private final BigDecimal quantity;
    @JsonSerialize(using = AmountSerializer.class)
    private final BigDecimal marketPrice;
    @JsonSerialize(using = AmountSerializer.class)
    private final BigDecimal marketValue;
    @JsonSerialize(using = PercentSerializer.class)
    private final BigDecimal totalReturnPercent;
    @JsonSerialize(using = PercentSerializer.class)
    private final BigDecimal dailyReturnPercent;
}
