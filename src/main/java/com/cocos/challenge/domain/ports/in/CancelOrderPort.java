package com.cocos.challenge.domain.ports.in;

import com.cocos.challenge.domain.models.OrderModel;

public interface CancelOrderPort {
    OrderModel execute(int userId, int orderId);
}
