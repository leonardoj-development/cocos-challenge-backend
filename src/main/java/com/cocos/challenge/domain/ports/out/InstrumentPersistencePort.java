package com.cocos.challenge.domain.ports.out;

import com.cocos.challenge.domain.models.InstrumentModel;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface InstrumentPersistencePort {

    Optional<InstrumentModel> findById(int instrumentId);

    Map<Integer, InstrumentModel> findByIds(Collection<Integer> instrumentIds);

    List<InstrumentModel> search(String text, int limit, int offset);

    List<InstrumentModel> findArsInstruments();
}
