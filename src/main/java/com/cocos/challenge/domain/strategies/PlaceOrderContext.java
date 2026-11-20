package com.cocos.challenge.domain.strategies;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.domain.strategies.orders.PlaceOrderStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Selects the single {@link PlaceOrderStrategy} that supports the side/type combination
 * and delegates the order decision to it.
 */
@Component
@RequiredArgsConstructor
public class PlaceOrderContext {

    private final List<PlaceOrderStrategy> strategies;

    public PlaceOrderDecisionModel apply(PlaceOrderCommand command, BigDecimal marketClose) {
        return select(command.side(), command.type()).apply(command, marketClose);
    }

    private PlaceOrderStrategy select(SideEnum side, OrderTypeEnum type) {
        List<PlaceOrderStrategy> matching = strategies.stream()
                .filter(strategy -> strategy.supports(side, type))
                .toList();
        if (matching.isEmpty()) {
            throw new IllegalStateException("No place-order strategy registered for " + side + " + " + type);
        }
        if (matching.size() > 1) {
            throw new IllegalStateException("More than one place-order strategy registered for "
                    + side + " + " + type + ": " + matching.size());
        }
        return matching.getFirst();
    }
}
