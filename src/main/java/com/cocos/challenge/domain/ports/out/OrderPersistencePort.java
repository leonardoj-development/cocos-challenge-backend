package com.cocos.challenge.domain.ports.out;

import com.cocos.challenge.domain.models.NewOrderModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;

import java.util.List;
import java.util.Optional;

public interface OrderPersistencePort {

    List<OrderModel> findByUserOrderedByDatetime(int userId);

    Optional<OrderModel> findByIdAndUser(int orderId, int userId);

    OrderModel insert(NewOrderModel order);

    boolean transitionStatus(int orderId, OrderStatusEnum from, OrderStatusEnum to);
}
