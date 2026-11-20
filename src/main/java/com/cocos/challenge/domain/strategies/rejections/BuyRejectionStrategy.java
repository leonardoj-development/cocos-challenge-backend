package com.cocos.challenge.domain.strategies.rejections;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BuyRejectionStrategy implements OrderRejectionStrategy {

    @Override
    public boolean supports(SideEnum side) {
        return SideEnum.BUY.equals(side);
    }

    @Override
    public RejectionReasonEnum apply(PlaceOrderCommand command, PlaceOrderDecisionModel decision,
                                     PositionsModel positions) {
        BigDecimal required = decision.quantity().multiply(decision.price());
        BigDecimal availableCash = positions.settledCash().subtract(positions.reservedCash());
        return required.compareTo(availableCash) > 0 ? RejectionReasonEnum.INSUFFICIENT_FUNDS : null;
    }
}
