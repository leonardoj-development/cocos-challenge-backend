package com.cocos.challenge.domain.strategies;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.domain.strategies.rejections.OrderRejectionStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Selects the single {@link OrderRejectionStrategy} that supports the order side and returns
 * the rejection reason, or {@code null} when the order can be accepted.
 */
@Component
@RequiredArgsConstructor
public class CheckOrderRejectionContext {

    private final List<OrderRejectionStrategy> strategies;

    public RejectionReasonEnum apply(PlaceOrderCommand command, PlaceOrderDecisionModel decision,
                                     PositionsModel positions) {
        return select(command.side()).apply(command, decision, positions);
    }

    private OrderRejectionStrategy select(SideEnum side) {
        List<OrderRejectionStrategy> matching = strategies.stream()
                .filter(strategy -> strategy.supports(side))
                .toList();
        if (matching.isEmpty()) {
            throw new IllegalStateException("No order-rejection strategy registered for " + side);
        }
        if (matching.size() > 1) {
            throw new IllegalStateException("More than one order-rejection strategy registered for "
                    + side + ": " + matching.size());
        }
        return matching.get(0);
    }
}
