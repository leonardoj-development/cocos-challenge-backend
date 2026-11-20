package com.cocos.challenge.domain.strategies.positions;

import com.cocos.challenge.domain.models.HoldingModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import org.springframework.stereotype.Component;

@Component
public class NewSellStrategy implements CalculatePositionsStrategy {

    @Override
    public boolean supports(OrderStatusEnum status, SideEnum side) {
        return OrderStatusEnum.NEW.equals(status) && SideEnum.SELL.equals(side);
    }

    @Override
    public PositionsModel apply(OrderModel order, PositionsModel state) {
        order.requireValidTrade();
        HoldingModel holding = state.holdingOf(order.instrumentId());
        HoldingModel updatedHolding = HoldingModel.builder()
                .instrumentId(order.instrumentId())
                .quantity(holding.quantity())
                .remainingCost(holding.remainingCost())
                .reservedShares(holding.reservedShares().add(order.size()))
                .build();
        return state.withHolding(order.instrumentId(), updatedHolding);
    }
}
