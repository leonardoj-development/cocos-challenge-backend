package com.cocos.challenge.infrastructure.rest.controllers.dtos;

import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CancelOrderResponseDto {
    private final int id;
    private final OrderStatusEnum status;
}
