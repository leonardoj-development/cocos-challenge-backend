package com.cocos.challenge.domain.strategies.positions;

import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;

public interface CalculatePositionsStrategy {

    boolean supports(OrderStatusEnum status, SideEnum side);

    PositionsModel apply(OrderModel order, PositionsModel state);
}
