package com.cocos.challenge.infrastructure.rest.controllers;

import com.cocos.challenge.domain.ports.in.GetPortfolioPort;
import com.cocos.challenge.infrastructure.rest.controllers.dtos.PortfolioResponseDto;
import com.cocos.challenge.infrastructure.rest.controllers.mappers.ControllerMapper;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PortfolioController {
    private final GetPortfolioPort getPortfolio;
    private final ControllerMapper mapper;

    @GetMapping("/api/v1/users/{userId}/portfolio")
    public PortfolioResponseDto get(@PathVariable @Positive int userId) {
        return mapper.toPortfolioResponse(getPortfolio.execute(userId));
    }
}
