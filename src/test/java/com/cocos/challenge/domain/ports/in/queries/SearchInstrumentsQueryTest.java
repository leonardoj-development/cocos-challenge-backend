package com.cocos.challenge.domain.ports.in.queries;

import com.cocos.challenge.domain.exceptions.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("Search instruments query")
class SearchInstrumentsQueryTest {
    @Test
    @DisplayName("Strips the text and keeps the pagination")
    void givenPaddedText_whenBuilding_thenTextIsStripped() {
        // Given
        SearchInstrumentsQuery.SearchInstrumentsQueryBuilder builder = SearchInstrumentsQuery.builder()
                .text("  ALPHA ").limit(100).offset(0);

        // When
        SearchInstrumentsQuery query = builder.build();

        // Then
        assertThat(query.text()).as("The text is stripped").isEqualTo("ALPHA");
        assertThat(query.limit()).as("The limit is kept").isEqualTo(100);
        assertThat(query.offset()).as("The offset is kept").isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Rejects a missing or blank text")
    void givenMissingOrBlankText_whenBuilding_thenRequestIsInvalid(String text) {
        // Given
        SearchInstrumentsQuery.SearchInstrumentsQueryBuilder builder = SearchInstrumentsQuery.builder()
                .text(text).limit(20).offset(0);

        // When
        Throwable failure = catchThrowable(builder::build);

        // Then
        assertThat(failure).as("A blank text is invalid").isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    @DisplayName("Rejects a limit outside 1..100")
    void givenLimitOutOfRange_whenBuilding_thenRequestIsInvalid(int limit) {
        // Given
        SearchInstrumentsQuery.SearchInstrumentsQueryBuilder builder = SearchInstrumentsQuery.builder()
                .text("a").limit(limit).offset(0);

        // When
        Throwable failure = catchThrowable(builder::build);

        // Then
        assertThat(failure).as("A limit outside 1..100 is invalid").isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("Rejects a negative offset")
    void givenNegativeOffset_whenBuilding_thenRequestIsInvalid() {
        // Given
        SearchInstrumentsQuery.SearchInstrumentsQueryBuilder builder = SearchInstrumentsQuery.builder()
                .text("a").limit(1).offset(-1);

        // When
        Throwable failure = catchThrowable(builder::build);

        // Then
        assertThat(failure).as("A negative offset is invalid").isInstanceOf(InvalidRequestException.class);
    }
}
