package com.cocos.challenge.domain.strategies.positions;

import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import org.springframework.stereotype.Component;

@Component
public class FilledCashInStrategy implements CalculatePositionsStrategy {

    @Override
    public boolean supports(OrderStatusEnum status, SideEnum side) {
        return OrderStatusEnum.FILLED.equals(status) && SideEnum.CASH_IN.equals(side);
    }

    @Override
    public PositionsModel apply(OrderModel order, PositionsModel state) {
        return state.withCash(state.settledCash().add(order.size()));
    }
}
