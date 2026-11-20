package com.cocos.challenge.infrastructure.persistence;

import com.cocos.challenge.domain.models.MarketDataModel;
import com.cocos.challenge.infrastructure.persistence.entities.MarketDataEntity;
import com.cocos.challenge.infrastructure.persistence.mappers.PersistenceMapper;
import com.cocos.challenge.infrastructure.persistence.repositories.MarketDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("Market data JPA adapter")
class MarketDataJpaAdapterTest {
    private MarketDataRepository repository;
    private PersistenceMapper mapper;
    private MarketDataJpaAdapter adapter;

    private static MarketDataEntity entity(int instrumentId) {
        MarketDataEntity entity = new MarketDataEntity();
        entity.setInstrumentId(instrumentId);
        return entity;
    }

    private static MarketDataModel model(int instrumentId) {
        return MarketDataModel.builder().instrumentId(instrumentId).close(BigDecimal.TEN)
                .previousClose(BigDecimal.ONE).date(LocalDate.of(2026, 1, 1)).build();
    }

    @BeforeEach
    void setUp() {
        repository = mock(MarketDataRepository.class);
        mapper = mock(PersistenceMapper.class);
        adapter = new MarketDataJpaAdapter(repository, mapper);
    }

    @Test
    @DisplayName("Maps the latest quote of an instrument")
    void givenInstrument_whenFindingLatestQuote_thenTheModelIsReturned() {
        // Given
        MarketDataEntity entity = entity(2);
        MarketDataModel model = model(2);
        when(repository.findFirstByInstrumentIdAndDateIsNotNullOrderByDateDescIdDesc(2)).thenReturn(Optional.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        // When
        Optional<MarketDataModel> result = adapter.findLatest(2);

        // Then
        assertThat(result).as("The latest quote is returned").contains(model);
    }

    @Test
    @DisplayName("Skips the repository for an empty id collection")
    void givenNoIds_whenFindingLatestQuotes_thenTheRepositoryIsNotQueried() {
        // When
        Map<Integer, MarketDataModel> result = adapter.findLatest(List.of());

        // Then
        assertThat(result).as("No lookup is needed").isEmpty();
        verifyNoInteractions(repository, mapper);
    }

    @Test
    @DisplayName("Maps matching quotes keyed by instrument id")
    void givenIds_whenFindingLatestQuotes_thenResultsAreKeyedByInstrumentId() {
        // Given
        MarketDataEntity firstEntity = entity(2);
        MarketDataEntity secondEntity = entity(3);
        when(repository.findLatest(List.of(2, 3))).thenReturn(List.of(firstEntity, secondEntity));
        when(mapper.toModel(firstEntity)).thenReturn(model(2));
        when(mapper.toModel(secondEntity)).thenReturn(model(3));

        // When
        Map<Integer, MarketDataModel> result = adapter.findLatest(List.of(2, 3));

        // Then
        assertThat(result.keySet()).as("Both instruments are present").containsExactlyInAnyOrder(2, 3);
        assertThat(result.get(2).instrumentId()).as("Instrument 2 maps to its quote").isEqualTo(2);
        assertThat(result.get(3).instrumentId()).as("Instrument 3 maps to its quote").isEqualTo(3);
    }
}
