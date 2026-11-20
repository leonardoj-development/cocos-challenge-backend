package com.cocos.challenge.infrastructure.persistence.mappers;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.models.*;
import com.cocos.challenge.infrastructure.persistence.entities.InstrumentEntity;
import com.cocos.challenge.infrastructure.persistence.entities.MarketDataEntity;
import com.cocos.challenge.infrastructure.persistence.entities.OrderEntity;
import com.cocos.challenge.infrastructure.persistence.entities.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = ComponentModel.SPRING)
public interface PersistenceMapper {
    UserModel toModel(UserEntity entity);

    InstrumentModel toModel(InstrumentEntity entity);

    MarketDataModel toModel(MarketDataEntity entity);

    @Mapping(target = "id", ignore = true)
    OrderEntity toEntity(NewOrderModel model);

    OrderModel mapOrder(OrderEntity entity);

    default OrderModel toModel(OrderEntity entity) {
        if (entity.getUserId() == null || entity.getInstrumentId() == null || entity.getSide() == null
                || entity.getStatus() == null || entity.getSize() == null || entity.getSize().signum() <= 0
                || entity.getDatetime() == null) {
            throw new InconsistentDataException("Order " + entity.getId() + " has missing or invalid fields");
        }
        try {
            return mapOrder(entity);
        } catch (IllegalArgumentException ex) {
            throw new InconsistentDataException("Order " + entity.getId() + " has unknown side, type or status", ex);
        }
    }

    default LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    default Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}
