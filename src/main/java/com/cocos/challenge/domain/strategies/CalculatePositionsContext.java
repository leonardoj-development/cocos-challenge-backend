package com.cocos.challenge.domain.strategies;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.strategies.positions.CalculatePositionsStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Folds the user's orders (already sorted by datetime) into {@link PositionsModel}, selecting
 * the single {@link CalculatePositionsStrategy} that supports each order's status and side,
 * and enforcing the settled-cash invariant after each step.
 */
@Component
@RequiredArgsConstructor
public class CalculatePositionsContext {

    private final List<CalculatePositionsStrategy> strategies;

    public PositionsModel apply(List<OrderModel> userOrders) {
        PositionsModel state = PositionsModel.builder()
                .settledCash(BigDecimal.ZERO)
                .reservedCash(BigDecimal.ZERO)
                .holdings(Map.of())
                .build();

        for (OrderModel order : userOrders) {
            state = select(order).apply(order, state);
            if (state.settledCash().signum() < 0) {
                throw new InconsistentDataException("Settled cash became negative at order " + order.id());
            }
        }

        return PositionsModel.builder()
                .settledCash(state.settledCash())
                .reservedCash(state.reservedCash())
                .holdings(Collections.unmodifiableMap(new TreeMap<>(state.holdings())))
                .build();
    }

    private CalculatePositionsStrategy select(OrderModel order) {
        List<CalculatePositionsStrategy> matching = strategies.stream()
                .filter(strategy -> strategy.supports(order.status(), order.side()))
                .toList();
        if (matching.isEmpty()) {
            throw new IllegalStateException("No positions strategy registered for "
                    + order.status() + " + " + order.side());
        }
        if (matching.size() > 1) {
            throw new IllegalStateException("More than one positions strategy registered for "
                    + order.status() + " + " + order.side() + ": " + matching.size());
        }
        return matching.get(0);
    }
}
