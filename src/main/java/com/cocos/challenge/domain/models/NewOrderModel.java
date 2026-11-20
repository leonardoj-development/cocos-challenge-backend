package com.cocos.challenge.domain.models;

import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record NewOrderModel(int userId, int instrumentId, SideEnum side, BigDecimal size, BigDecimal price,
                            OrderTypeEnum type, OrderStatusEnum status, Instant datetime) {
}
