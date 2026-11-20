package com.cocos.challenge.domain.models;

import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import lombok.Builder;

@Builder
public record PlaceOrderResultModel(OrderModel order, RejectionReasonEnum rejectionReason) {
}
