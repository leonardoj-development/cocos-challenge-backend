package com.cocos.challenge.domain.strategies.rejections;

import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.PositionsModel;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;

public interface OrderRejectionStrategy {

    boolean supports(SideEnum side);

    RejectionReasonEnum apply(PlaceOrderCommand command, PlaceOrderDecisionModel decision, PositionsModel positions);
}
