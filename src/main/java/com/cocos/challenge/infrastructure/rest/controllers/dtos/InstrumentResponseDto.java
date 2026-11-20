package com.cocos.challenge.infrastructure.rest.controllers.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InstrumentResponseDto {
    private final int id;
    private final String ticker;
    private final String name;
    private final String type;
}
