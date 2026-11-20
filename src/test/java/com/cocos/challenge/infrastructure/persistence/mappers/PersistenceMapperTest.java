package com.cocos.challenge.infrastructure.persistence.mappers;

import com.cocos.challenge.domain.exceptions.InconsistentDataException;
import com.cocos.challenge.domain.models.NewOrderModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.infrastructure.persistence.entities.InstrumentEntity;
import com.cocos.challenge.infrastructure.persistence.entities.MarketDataEntity;
import com.cocos.challenge.infrastructure.persistence.entities.OrderEntity;
import com.cocos.challenge.infrastructure.persistence.entities.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("Persistence mapper")
class PersistenceMapperTest {
    private PersistenceMapper mapper;

    private static OrderEntity validOrderEntity() {
        OrderEntity entity = new OrderEntity();
        entity.setId(1);
        entity.setUserId(2);
        entity.setInstrumentId(3);
        entity.setSide("BUY");
        entity.setSize(BigDecimal.TEN);
        entity.setPrice(new BigDecimal("12.50"));
        entity.setType("MARKET");
        entity.setStatus("FILLED");
        entity.setDatetime(LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123_456_000));
        return entity;
    }

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(PersistenceMapper.class);
    }

    @Test
    @DisplayName("Maps a user entity to its model")
    void givenUserEntity_whenMapping_thenFieldsAreCopied() {
        // Given
        UserEntity entity = new UserEntity();
        entity.setId(1);
        entity.setEmail("a@example.test");
        entity.setAccountNumber("A-1");

        // When
        var model = mapper.toModel(entity);

        // Then
        assertThat(model.id()).as("The id is copied").isEqualTo(1);
        assertThat(model.email()).as("The email is copied").isEqualTo("a@example.test");
        assertThat(model.accountNumber()).as("The account number is copied").isEqualTo("A-1");
    }

    @Test
    @DisplayName("Maps an instrument entity to its model")
    void givenInstrumentEntity_whenMapping_thenFieldsAreCopied() {
        // Given
        InstrumentEntity entity = new InstrumentEntity();
        entity.setId(2);
        entity.setTicker("GGAL");
        entity.setName("Grupo Galicia");
        entity.setType("ACCIONES");

        // When
        var model = mapper.toModel(entity);

        // Then
        assertThat(model.id()).as("The id is copied").isEqualTo(2);
        assertThat(model.ticker()).as("The ticker is copied").isEqualTo("GGAL");
        assertThat(model.name()).as("The name is copied").isEqualTo("Grupo Galicia");
        assertThat(model.type()).as("The type is copied").isEqualTo("ACCIONES");
    }

    @Test
    @DisplayName("Maps a market data entity to its model")
    void givenMarketDataEntity_whenMapping_thenFieldsAreCopied() {
        // Given
        MarketDataEntity entity = new MarketDataEntity();
        entity.setId(3);
        entity.setInstrumentId(2);
        entity.setClose(new BigDecimal("100.00"));
        entity.setPreviousClose(new BigDecimal("95.00"));
        entity.setDate(LocalDate.of(2026, 1, 1));

        // When
        var model = mapper.toModel(entity);

        // Then
        assertThat(model.instrumentId()).as("The instrument id is copied").isEqualTo(2);
        assertThat(model.close()).as("The close is copied").isEqualByComparingTo("100.00");
        assertThat(model.previousClose()).as("The previous close is copied").isEqualByComparingTo("95.00");
        assertThat(model.date()).as("The date is copied").isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("Maps a valid order entity preserving UTC timestamps")
    void givenValidOrderEntity_whenMapping_thenTimestampRemainsUtc() {
        // Given
        OrderEntity entity = validOrderEntity();

        // When
        var model = mapper.toModel(entity);

        // Then
        assertThat(model.side()).as("The side is parsed").isEqualTo(SideEnum.BUY);
        assertThat(model.type()).as("The type is parsed").isEqualTo(OrderTypeEnum.MARKET);
        assertThat(model.status()).as("The status is parsed").isEqualTo(OrderStatusEnum.FILLED);
        assertThat(model.datetime()).as("The stored wall time is read back as the same UTC instant")
                .isEqualTo(Instant.parse("2026-03-04T05:06:07.123456Z"));
    }

    @Test
    @DisplayName("Converts a new order model into an entity with UTC wall time")
    void givenNewOrderModel_whenMappingToEntity_thenEnumsAndTimestampAreConverted() {
        // Given
        NewOrderModel model = NewOrderModel.builder().userId(2).instrumentId(3).side(SideEnum.SELL)
                .size(BigDecimal.ONE).price(new BigDecimal("10.00")).type(OrderTypeEnum.LIMIT)
                .status(OrderStatusEnum.NEW).datetime(Instant.parse("2026-03-04T05:06:07.123456Z")).build();

        // When
        OrderEntity entity = mapper.toEntity(model);

        // Then
        assertThat(entity.getId()).as("The id is not assigned by the mapper").isNull();
        assertThat(entity.getSide()).as("The side is stored by name").isEqualTo("SELL");
        assertThat(entity.getType()).as("The type is stored by name").isEqualTo("LIMIT");
        assertThat(entity.getStatus()).as("The status is stored by name").isEqualTo("NEW");
        assertThat(entity.getDatetime()).as("The UTC instant is stored as the equivalent wall time")
                .isEqualTo(LocalDateTime.of(2026, 3, 4, 5, 6, 7, 123_456_000));
    }

    @Test
    @DisplayName("Rejects an order missing required fields")
    void givenOrderEntityWithMissingFields_whenMapping_thenInconsistentDataIsReported() {
        // Given
        OrderEntity missingUser = validOrderEntity();
        missingUser.setUserId(null);
        OrderEntity missingInstrument = validOrderEntity();
        missingInstrument.setInstrumentId(null);
        OrderEntity missingSide = validOrderEntity();
        missingSide.setSide(null);
        OrderEntity missingStatus = validOrderEntity();
        missingStatus.setStatus(null);
        OrderEntity missingSize = validOrderEntity();
        missingSize.setSize(null);
        OrderEntity nonPositiveSize = validOrderEntity();
        nonPositiveSize.setSize(BigDecimal.ZERO);
        OrderEntity missingDatetime = validOrderEntity();
        missingDatetime.setDatetime(null);

        // When
        Throwable userFailure = catchThrowable(() -> mapper.toModel(missingUser));
        Throwable instrumentFailure = catchThrowable(() -> mapper.toModel(missingInstrument));
        Throwable sideFailure = catchThrowable(() -> mapper.toModel(missingSide));
        Throwable statusFailure = catchThrowable(() -> mapper.toModel(missingStatus));
        Throwable sizeFailure = catchThrowable(() -> mapper.toModel(missingSize));
        Throwable nonPositiveSizeFailure = catchThrowable(() -> mapper.toModel(nonPositiveSize));
        Throwable datetimeFailure = catchThrowable(() -> mapper.toModel(missingDatetime));

        // Then
        assertThat(userFailure).as("A missing user is reported").isInstanceOf(InconsistentDataException.class);
        assertThat(instrumentFailure).as("A missing instrument is reported").isInstanceOf(InconsistentDataException.class);
        assertThat(sideFailure).as("A missing side is reported").isInstanceOf(InconsistentDataException.class);
        assertThat(statusFailure).as("A missing status is reported").isInstanceOf(InconsistentDataException.class);
        assertThat(sizeFailure).as("A missing size is reported").isInstanceOf(InconsistentDataException.class);
        assertThat(nonPositiveSizeFailure).as("A non-positive size is reported").isInstanceOf(InconsistentDataException.class);
        assertThat(datetimeFailure).as("A missing datetime is reported").isInstanceOf(InconsistentDataException.class);
    }

    @Test
    @DisplayName("Rejects an order with an unknown enum value")
    void givenOrderEntityWithUnknownEnumValue_whenMapping_thenInconsistentDataIsReported() {
        // Given
        OrderEntity unknownSide = validOrderEntity();
        unknownSide.setSide("UNKNOWN");

        // When
        Throwable failure = catchThrowable(() -> mapper.toModel(unknownSide));

        // Then
        assertThat(failure).as("The unknown side is reported").isInstanceOf(InconsistentDataException.class)
                .hasMessageContaining("unknown side, type or status");
    }
}
