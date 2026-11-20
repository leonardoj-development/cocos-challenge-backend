package com.cocos.challenge.infrastructure.rest.controllers.dtos;

import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.infrastructure.rest.controllers.serializers.AmountSerializer;
import com.cocos.challenge.infrastructure.rest.controllers.serializers.QuantitySerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class OrderResponseDto {
    private final int id;
    private final int userId;
    private final int instrumentId;
    private final SideEnum side;
    private final OrderTypeEnum type;
    @JsonSerialize(using = QuantitySerializer.class)
    private final BigDecimal quantity;
    @JsonSerialize(using = AmountSerializer.class)
    private final BigDecimal price;
    private final OrderStatusEnum status;
    private final Instant datetime;
    private final RejectionReasonEnum rejectionReason;
}
