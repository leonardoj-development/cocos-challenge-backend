package com.cocos.challenge.infrastructure.persistence.repositories;

import com.cocos.challenge.infrastructure.persistence.entities.InstrumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Integer> {
    List<InstrumentEntity> findByTickerAndTypeOrderById(String ticker, String type);

    @Query(value = "SELECT * FROM instruments WHERE ticker ILIKE :pattern ESCAPE '\\' "
            + "OR name ILIKE :pattern ESCAPE '\\' ORDER BY ticker, id LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<InstrumentEntity> search(@Param("pattern") String pattern, @Param("limit") int limit,
                                  @Param("offset") int offset);
}
