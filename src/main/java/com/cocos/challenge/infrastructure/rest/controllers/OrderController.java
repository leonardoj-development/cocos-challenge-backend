package com.cocos.challenge.infrastructure.rest.controllers;

import com.cocos.challenge.domain.ports.in.CancelOrderPort;
import com.cocos.challenge.domain.ports.in.PlaceOrderPort;
import com.cocos.challenge.infrastructure.rest.controllers.dtos.CancelOrderResponseDto;
import com.cocos.challenge.infrastructure.rest.controllers.dtos.OrderResponseDto;
import com.cocos.challenge.infrastructure.rest.controllers.dtos.PlaceOrderRequestDto;
import com.cocos.challenge.infrastructure.rest.controllers.mappers.ControllerMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/{userId}/orders")
@RequiredArgsConstructor
public class OrderController {
    private final PlaceOrderPort placeOrder;
    private final CancelOrderPort cancelOrder;
    private final ControllerMapper mapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponseDto place(@PathVariable @Positive int userId, @Valid @RequestBody PlaceOrderRequestDto request) {
        return mapper.toOrderResponse(placeOrder.execute(userId, mapper.toCommand(request)));
    }

    @PostMapping("/{orderId}/cancel")
    public CancelOrderResponseDto cancel(@PathVariable @Positive int userId, @PathVariable @Positive int orderId) {
        return mapper.toCancelOrderResponse(cancelOrder.execute(userId, orderId));
    }
}
