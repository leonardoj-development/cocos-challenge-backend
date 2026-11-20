package com.cocos.challenge.infrastructure.rest.controllers.dtos;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class InstrumentSearchResponseDto {
    private final List<InstrumentResponseDto> items;
    private final int limit;
    private final int offset;
}
