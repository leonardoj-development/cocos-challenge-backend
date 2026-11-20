package com.cocos.challenge.domain.models;

import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;

import java.math.BigDecimal;

public record PlaceOrderDecisionModel(BigDecimal quantity, BigDecimal price, OrderStatusEnum status,
                                      RejectionReasonEnum rejection) {
}
