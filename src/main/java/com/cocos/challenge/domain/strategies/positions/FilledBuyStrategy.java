package com.cocos.challenge.domain.strategies.positions;

import com.cocos.challenge.domain.models.HoldingModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

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
        BigDecimal quantity = holding.quantity().add(order.size());
        BigDecimal remainingCost;
        if (holding.quantity().signum() >= 0) {
            remainingCost = holding.remainingCost().add(value);
        } else if (quantity.signum() == 0) {
            remainingCost = BigDecimal.ZERO;
        } else if (quantity.signum() < 0) {
            remainingCost = holding.remainingCost().multiply(quantity)
                    .divide(holding.quantity(), MathContext.DECIMAL128);
        } else {
            remainingCost = quantity.multiply(order.price());
        }
        HoldingModel updatedHolding = HoldingModel.builder()
                .instrumentId(order.instrumentId())
                .quantity(quantity)
                .remainingCost(remainingCost)
                .reservedShares(holding.reservedShares())
                .build();
        return state.withCashAndHolding(state.settledCash().subtract(value), order.instrumentId(), updatedHolding);
    }
}
