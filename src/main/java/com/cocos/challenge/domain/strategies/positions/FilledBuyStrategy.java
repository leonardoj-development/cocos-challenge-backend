package com.cocos.challenge.domain.strategies.positions;

import com.cocos.challenge.domain.models.HoldingModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FilledBuyStrategy implements CalculatePositionsStrategy {

    @Override
    public boolean supports(OrderStatusEnum status, SideEnum side) {
        return OrderStatusEnum.FILLED.equals(status) && SideEnum.BUY.equals(side);
    }

    @Override
    public PositionsModel apply(OrderModel order, PositionsModel state) {
        order.requireValidTrade();
        HoldingModel holding = state.holdingOf(order.instrumentId());
        BigDecimal value = order.size().multiply(order.price());
        HoldingModel updatedHolding = HoldingModel.builder()
                .instrumentId(order.instrumentId())
                .quantity(holding.quantity().add(order.size()))
                .remainingCost(holding.remainingCost().add(value))
                .reservedShares(holding.reservedShares())
                .build();
        return state.withCashAndHolding(state.settledCash().subtract(value), order.instrumentId(), updatedHolding);
    }
}
