package com.cocos.challenge.domain.ports.in;

import com.cocos.challenge.domain.models.PortfolioModel;

public interface GetPortfolioPort {
    PortfolioModel execute(int userId);
}
