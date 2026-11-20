package com.cocos.challenge.infrastructure.persistence;

import com.cocos.challenge.domain.models.MarketDataModel;
import com.cocos.challenge.domain.ports.out.MarketDataPersistencePort;
import com.cocos.challenge.infrastructure.persistence.mappers.PersistenceMapper;
import com.cocos.challenge.infrastructure.persistence.repositories.MarketDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MarketDataJpaAdapter implements MarketDataPersistencePort {
    private final MarketDataRepository repository;
    private final PersistenceMapper mapper;

    @Override
    public Optional<MarketDataModel> findLatest(int instrumentId) {
        return repository.findFirstByInstrumentIdAndDateIsNotNullOrderByDateDescIdDesc(instrumentId).map(mapper::toModel);
    }

    @Override
    public Map<Integer, MarketDataModel> findLatest(Collection<Integer> instrumentIds) {
        Map<Integer, MarketDataModel> result = new LinkedHashMap<>();
        if (!instrumentIds.isEmpty()) {
            repository.findLatest(instrumentIds).stream().map(mapper::toModel)
                    .forEach(quote -> result.put(quote.instrumentId(), quote));
        }
        return result;
    }
}
