package com.cocos.challenge.domain.strategies.rejections;

import com.cocos.challenge.domain.models.HoldingModel;
import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SellRejectionStrategy implements OrderRejectionStrategy {

    @Override
    public boolean supports(SideEnum side) {
        return SideEnum.SELL.equals(side);
    }

    @Override
    public RejectionReasonEnum apply(PlaceOrderCommand command, PlaceOrderDecisionModel decision,
                                     PositionsModel positions) {
        HoldingModel holding = positions.holdingOf(command.instrumentId());
        BigDecimal availableShares = holding.quantity().subtract(holding.reservedShares());
        return decision.quantity().compareTo(availableShares) > 0 ? RejectionReasonEnum.INSUFFICIENT_SHARES : null;
    }
}
