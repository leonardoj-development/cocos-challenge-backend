package com.cocos.challenge.application;

import com.cocos.challenge.domain.exceptions.OrderNotCancellableException;
import com.cocos.challenge.domain.exceptions.OrderNotFoundException;
import com.cocos.challenge.domain.exceptions.UserNotFoundException;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.ports.in.CancelOrderPort;
import com.cocos.challenge.domain.ports.out.OrderPersistencePort;
import com.cocos.challenge.domain.ports.out.UserPersistencePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CancelOrderUseCase implements CancelOrderPort {
    private final UserPersistencePort users;
    private final OrderPersistencePort orders;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public OrderModel execute(int userId, int orderId) {
        users.lockById(userId).orElseThrow(() -> new UserNotFoundException("User " + userId + " not found"));
        OrderModel order = orders.findByIdAndUser(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));
        boolean isCancellable = OrderStatusEnum.NEW.equals(order.status())
                && orders.transitionStatus(orderId, OrderStatusEnum.NEW, OrderStatusEnum.CANCELLED);
        if (!isCancellable) {
            throw new OrderNotCancellableException("Order " + orderId + " is not cancellable");
        }
        return orders.findByIdAndUser(orderId, userId).orElseThrow(
                () -> new OrderNotFoundException("Order " + orderId + " not found"));
    }
}
