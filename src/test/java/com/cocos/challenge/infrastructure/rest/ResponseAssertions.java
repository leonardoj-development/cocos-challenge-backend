package com.cocos.challenge.infrastructure.rest;

import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Adds context to MockMvc assertion failures.
 */
public final class ResponseAssertions {
    private ResponseAssertions() {
    }

    public static ResultMatcher described(String description, ResultMatcher matcher) {
        return result -> {
            try {
                matcher.match(result);
            } catch (AssertionError failure) {
                throw new AssertionError(description + ": " + failure.getMessage(), failure);
            }
        };
    }
}
