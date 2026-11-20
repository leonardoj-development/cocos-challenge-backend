package com.cocos.challenge.infrastructure.persistence;

import com.cocos.challenge.domain.models.InstrumentModel;
import com.cocos.challenge.domain.ports.out.InstrumentPersistencePort;
import com.cocos.challenge.infrastructure.persistence.mappers.PersistenceMapper;
import com.cocos.challenge.infrastructure.persistence.repositories.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@RequiredArgsConstructor
public class InstrumentJpaAdapter implements InstrumentPersistencePort {
    private final InstrumentRepository repository;
    private final PersistenceMapper mapper;

    @Override
    public Optional<InstrumentModel> findById(int instrumentId) {
        return repository.findById(instrumentId).map(mapper::toModel);
    }

    @Override
    public Map<Integer, InstrumentModel> findByIds(Collection<Integer> instrumentIds) {
        Map<Integer, InstrumentModel> result = new LinkedHashMap<>();
        if (!instrumentIds.isEmpty()) {
            repository.findAllById(instrumentIds).stream().map(mapper::toModel)
                    .sorted(java.util.Comparator.comparingInt(InstrumentModel::id))
                    .forEach(instrument -> result.put(instrument.id(), instrument));
        }
        return result;
    }

    @Override
    public List<InstrumentModel> search(String text, int limit, int offset) {
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return repository.search(pattern, limit, offset).stream().map(mapper::toModel).toList();
    }

    @Override
    public List<InstrumentModel> findArsInstruments() {
        return repository.findByTickerAndTypeOrderById("ARS", "MONEDA").stream().map(mapper::toModel).toList();
    }
}
