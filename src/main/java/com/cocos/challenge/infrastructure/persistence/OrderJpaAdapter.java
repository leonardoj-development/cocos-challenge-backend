package com.cocos.challenge.infrastructure.persistence;

import com.cocos.challenge.domain.exceptions.InvalidOrderException;
import com.cocos.challenge.domain.models.NewOrderModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.ports.out.OrderPersistencePort;
import com.cocos.challenge.infrastructure.persistence.entities.OrderEntity;
import com.cocos.challenge.infrastructure.persistence.mappers.PersistenceMapper;
import com.cocos.challenge.infrastructure.persistence.repositories.OrderRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderJpaAdapter implements OrderPersistencePort {
    private static final BigDecimal MAX_ORDER_SIZE = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal MAX_ORDER_PRICE = new BigDecimal("99999999.99");

    private final OrderRepository repository;
    private final PersistenceMapper mapper;
    private final EntityManager entityManager;

    private static boolean fitsOrderSizeColumn(BigDecimal size) {
        return OrderModel.isWholeShareQuantity(size) && size.compareTo(MAX_ORDER_SIZE) <= 0;
    }

    private static boolean fitsOrderPriceColumn(BigDecimal price) {
        return price.compareTo(MAX_ORDER_PRICE) <= 0
                && (price.scale() <= 2 || price.stripTrailingZeros().scale() <= 2);
    }

    @Override
    public List<OrderModel> findByUserOrderedByDatetime(int userId) {
        return repository.findByUserIdOrderByDatetimeAscIdAsc(userId).stream().map(mapper::toModel).toList();
    }

    @Override
    public Optional<OrderModel> findByIdAndUser(int orderId, int userId) {
        return repository.findByIdAndUserId(orderId, userId).map(mapper::toModel);
    }

    @Override
    @Transactional
    public OrderModel insert(NewOrderModel order) {
        if (order.size() == null || order.size().signum() <= 0
                || !fitsOrderSizeColumn(order.size())
                || (order.price() != null && (order.price().signum() <= 0
                || !fitsOrderPriceColumn(order.price())))) {
            throw new InvalidOrderException("Order size or price cannot be stored without rounding");
        }
        OrderEntity entity = repository.saveAndFlush(mapper.toEntity(order));
        entityManager.refresh(entity);
        return mapper.toModel(entity);
    }

    @Override
    public boolean transitionStatus(int orderId, OrderStatusEnum from, OrderStatusEnum to) {
        return repository.transitionStatus(orderId, from.name(), to.name()) == 1;
    }
}
