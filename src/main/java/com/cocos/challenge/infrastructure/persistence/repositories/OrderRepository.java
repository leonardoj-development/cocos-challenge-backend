package com.cocos.challenge.infrastructure.persistence.repositories;

import com.cocos.challenge.infrastructure.persistence.entities.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Integer> {
    List<OrderEntity> findByUserIdOrderByDatetimeAscIdAsc(int userId);

    Optional<OrderEntity> findByIdAndUserId(int id, int userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update OrderEntity o set o.status = :to where o.id = :id and o.status = :from")
    int transitionStatus(@Param("id") int id, @Param("from") String from, @Param("to") String to);
}
