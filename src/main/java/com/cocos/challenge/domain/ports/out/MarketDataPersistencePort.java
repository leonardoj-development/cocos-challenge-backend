package com.cocos.challenge.domain.ports.out;

import com.cocos.challenge.domain.models.MarketDataModel;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface MarketDataPersistencePort {

    Optional<MarketDataModel> findLatest(int instrumentId);

    Map<Integer, MarketDataModel> findLatest(Collection<Integer> instrumentIds);
}
