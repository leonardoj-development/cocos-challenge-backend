package com.cocos.challenge.infrastructure.persistence;

import com.cocos.challenge.domain.models.InstrumentModel;
import com.cocos.challenge.infrastructure.persistence.entities.InstrumentEntity;
import com.cocos.challenge.infrastructure.persistence.mappers.PersistenceMapper;
import com.cocos.challenge.infrastructure.persistence.repositories.InstrumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Instrument JPA adapter")
class InstrumentJpaAdapterTest {
    private InstrumentRepository repository;
    private PersistenceMapper mapper;
    private InstrumentJpaAdapter adapter;

    private static InstrumentEntity entity(int id) {
        InstrumentEntity entity = new InstrumentEntity();
        entity.setId(id);
        return entity;
    }

    private static InstrumentModel model(int id, String ticker) {
        return InstrumentModel.builder().id(id).ticker(ticker).name(ticker).type("ACCIONES").build();
    }

    @BeforeEach
    void setUp() {
        repository = mock(InstrumentRepository.class);
        mapper = mock(PersistenceMapper.class);
        adapter = new InstrumentJpaAdapter(repository, mapper);
    }

    @Test
    @DisplayName("Maps a found instrument by id")
    void givenExistingInstrument_whenFindingById_thenTheModelIsReturned() {
        // Given
        InstrumentEntity entity = entity(1);
        InstrumentModel model = model(1, "GGAL");
        when(repository.findById(1)).thenReturn(Optional.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        // When
        Optional<InstrumentModel> result = adapter.findById(1);

        // Then
        assertThat(result).as("The mapped instrument is returned").contains(model);
    }

    @Test
    @DisplayName("Skips the repository for an empty id collection")
    void givenNoIds_whenFindingByIds_thenTheRepositoryIsNotQueried() {
        // When
        Map<Integer, InstrumentModel> result = adapter.findByIds(List.of());

        // Then
        assertThat(result).as("No lookup is needed").isEmpty();
        verifyNoInteractions(repository, mapper);
    }

    @Test
    @DisplayName("Maps matching instruments keyed and ordered by id")
    void givenIds_whenFindingByIds_thenResultsAreKeyedAndOrderedById() {
        // Given
        InstrumentEntity second = entity(2);
        InstrumentEntity first = entity(1);
        when(repository.findAllById(List.of(2, 1))).thenReturn(List.of(second, first));
        when(mapper.toModel(second)).thenReturn(model(2, "BETA"));
        when(mapper.toModel(first)).thenReturn(model(1, "ALPH"));

        // When
        Map<Integer, InstrumentModel> result = adapter.findByIds(List.of(2, 1));

        // Then
        assertThat(result.keySet()).as("Results are ordered by instrument id").containsExactly(1, 2);
        assertThat(result.get(1).ticker()).as("Id 1 maps to ALPH").isEqualTo("ALPH");
        assertThat(result.get(2).ticker()).as("Id 2 maps to BETA").isEqualTo("BETA");
    }

    @Test
    @DisplayName("Escapes wildcard characters before searching")
    void givenTextWithWildcards_whenSearching_thenCharactersAreEscapedAndWrapped() {
        // Given
        String text = "100%_off\\path";
        when(repository.search(anyString(), eq(20), eq(0))).thenReturn(List.of());

        // When
        List<InstrumentModel> result = adapter.search(text, 20, 0);

        // Then
        assertThat(result).as("No matches for the stubbed call").isEmpty();
        verify(repository).search("%100\\%\\_off\\\\path%", 20, 0);
    }

    @Test
    @DisplayName("Finds the ARS currency instruments")
    void givenArsCurrency_whenFindingArsInstruments_thenTheRepositoryQueryIsUsed() {
        // Given
        InstrumentEntity entity = entity(5);
        InstrumentModel model = model(5, "ARS");
        when(repository.findByTickerAndTypeOrderById("ARS", "MONEDA")).thenReturn(List.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        // When
        List<InstrumentModel> result = adapter.findArsInstruments();

        // Then
        assertThat(result).as("The ARS instrument is returned").containsExactly(model);
    }
}
