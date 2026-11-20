package com.cocos.challenge.domain.ports.in.queries;

import com.cocos.challenge.domain.exceptions.InvalidRequestException;
import lombok.Builder;

@Builder
public record SearchInstrumentsQuery(String text, int limit, int offset) {
    private static final int MAX_LIMIT = 100;

    public SearchInstrumentsQuery {
        text = text == null ? "" : text.strip();
        if (text.isEmpty()) {
            throw new InvalidRequestException("query must not be blank");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new InvalidRequestException("offset must not be negative");
        }
    }
}
