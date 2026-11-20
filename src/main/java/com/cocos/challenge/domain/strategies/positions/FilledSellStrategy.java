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
public class FilledSellStrategy implements CalculatePositionsStrategy {

    @Override
    public boolean supports(OrderStatusEnum status, SideEnum side) {
        return OrderStatusEnum.FILLED.equals(status) && SideEnum.SELL.equals(side);
    }

    @Override
    public PositionsModel apply(OrderModel order, PositionsModel state) {
        order.requireValidTrade();
        HoldingModel holding = state.holdingOf(order.instrumentId());
        BigDecimal remaining = holding.quantity().subtract(order.size());
        BigDecimal remainingCost;
        if (remaining.signum() == 0) {
            remainingCost = BigDecimal.ZERO;
        } else if (holding.quantity().signum() == 0) {
            remainingCost = order.size().multiply(order.price()).negate();
        } else {
            remainingCost = holding.remainingCost().multiply(remaining)
                    .divide(holding.quantity(), MathContext.DECIMAL128);
        }
        HoldingModel updatedHolding = HoldingModel.builder()
                .instrumentId(order.instrumentId())
                .quantity(remaining)
                .remainingCost(remainingCost)
                .reservedShares(holding.reservedShares())
                .build();
        return state.withCashAndHolding(state.settledCash().add(order.size().multiply(order.price())),
                order.instrumentId(), updatedHolding);
    }
}
