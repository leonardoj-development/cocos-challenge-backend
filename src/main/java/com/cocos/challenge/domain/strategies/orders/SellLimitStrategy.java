package com.cocos.challenge.domain.strategies.orders;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SellLimitStrategy implements PlaceOrderStrategy {

    @Override
    public boolean supports(SideEnum side, OrderTypeEnum type) {
        return SideEnum.SELL.equals(side) && OrderTypeEnum.LIMIT.equals(type);
    }

    @Override
    public PlaceOrderDecisionModel apply(PlaceOrderCommand command, BigDecimal marketClose) {
        BigDecimal price = command.limitPrice();
        return new PlaceOrderDecisionModel(resolveQuantity(command, price), price, OrderStatusEnum.NEW, null);
    }
}
