package com.cocos.challenge.infrastructure.persistence.repositories;

import com.cocos.challenge.infrastructure.persistence.entities.MarketDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarketDataRepository extends JpaRepository<MarketDataEntity, Integer> {
    Optional<MarketDataEntity> findFirstByInstrumentIdAndDateIsNotNullOrderByDateDescIdDesc(int instrumentId);

    @Query(value = "SELECT DISTINCT ON (instrumentid) * FROM marketdata "
            + "WHERE instrumentid IN (:ids) AND date IS NOT NULL ORDER BY instrumentid, date DESC, id DESC",
            nativeQuery = true)
    List<MarketDataEntity> findLatest(@Param("ids") Collection<Integer> ids);
}
