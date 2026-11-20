package com.cocos.challenge.domain.models;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record OrderModel(int id, int userId, int instrumentId, SideEnum side, BigDecimal size, BigDecimal price,
                         OrderTypeEnum type, OrderStatusEnum status, Instant datetime) {

    public static boolean isWholeShareQuantity(BigDecimal quantity) {
        return quantity.scale() <= 0 || quantity.stripTrailingZeros().scale() <= 0;
    }

    public void requireValidTrade() {
        if (!isWholeShareQuantity(size)) {
            throw new InconsistentDataException("Order " + id + " has fractional shares");
        }
        if (price == null || price.signum() <= 0) {
            throw new InconsistentDataException("Order " + id + " has no positive price");
        }
    }
}
