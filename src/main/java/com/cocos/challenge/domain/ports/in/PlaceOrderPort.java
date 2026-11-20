package com.cocos.challenge.domain.ports.in;

import com.cocos.challenge.domain.models.PlaceOrderResultModel;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;

public interface PlaceOrderPort {
    PlaceOrderResultModel execute(int userId, PlaceOrderCommand command);
}
