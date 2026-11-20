package com.cocos.challenge.application;

import com.cocos.challenge.domain.models.InstrumentModel;
import com.cocos.challenge.domain.ports.in.queries.SearchInstrumentsQuery;
import com.cocos.challenge.domain.ports.out.InstrumentPersistencePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Search instruments use case")
class SearchInstrumentsUseCaseTest {
    @Test
    @DisplayName("Searches with the normalized text and the pagination of the query")
    void givenQuery_whenExecuting_thenPersistenceIsSearchedWithItsValues() {
        // Given
        InstrumentPersistencePort instruments = mock(InstrumentPersistencePort.class);
        InstrumentModel found = new InstrumentModel(2, "AAA", "Activo A", "ACCIONES");
        when(instruments.search("ALPHA", 5, 10)).thenReturn(List.of(found));
        SearchInstrumentsUseCase useCase = new SearchInstrumentsUseCase(instruments);
        SearchInstrumentsQuery query = SearchInstrumentsQuery.builder().text(" ALPHA ").limit(5).offset(10).build();

        // When
        List<InstrumentModel> result = useCase.execute(query);

        // Then
        assertThat(result).as("The persisted instruments are returned").containsExactly(found);
    }
}
