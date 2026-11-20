package com.cocos.challenge.domain.models;

import lombok.Builder;

@Builder
public record InstrumentModel(int id, String ticker, String name, String type) {
}
