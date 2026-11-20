package com.cocos.challenge.infrastructure.persistence;

import com.cocos.challenge.domain.exceptions.InvalidOrderException;
import com.cocos.challenge.domain.models.NewOrderModel;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.infrastructure.persistence.entities.OrderEntity;
import com.cocos.challenge.infrastructure.persistence.mappers.PersistenceMapper;
import com.cocos.challenge.infrastructure.persistence.repositories.OrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

@DisplayName("Order JPA adapter")
class OrderJpaAdapterTest {
    private OrderRepository repository;
    private PersistenceMapper mapper;
    private EntityManager entityManager;
    private OrderJpaAdapter adapter;

    private static NewOrderModel.NewOrderModelBuilder validOrder() {
        return NewOrderModel.builder().userId(1).instrumentId(2).side(SideEnum.BUY).type(OrderTypeEnum.MARKET)
                .status(OrderStatusEnum.FILLED).size(BigDecimal.TEN).price(new BigDecimal("10.00"));
    }

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        mapper = mock(PersistenceMapper.class);
        entityManager = mock(EntityManager.class);
        adapter = new OrderJpaAdapter(repository, mapper, entityManager);
    }

    @Test
    @DisplayName("Maps the orders of a user ordered by the repository")
    void givenUser_whenFindingOrders_thenTheyAreMapped() {
        // Given
        OrderEntity entity = new OrderEntity();
        OrderModel model = mock(OrderModel.class);
        when(repository.findByUserIdOrderByDatetimeAscIdAsc(1)).thenReturn(List.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        // When
        List<OrderModel> result = adapter.findByUserOrderedByDatetime(1);

        // Then
        assertThat(result).as("The mapped order is returned").containsExactly(model);
    }

    @Test
    @DisplayName("Maps a found order by id and user")
    void givenOrderAndUserId_whenFinding_thenTheModelIsReturned() {
        // Given
        OrderEntity entity = new OrderEntity();
        OrderModel model = mock(OrderModel.class);
        when(repository.findByIdAndUserId(5, 1)).thenReturn(Optional.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        // When
        Optional<OrderModel> result = adapter.findByIdAndUser(5, 1);

        // Then
        assertThat(result).as("The mapped order is returned").contains(model);
    }

    @Test
    @DisplayName("Rejects an order whose size or price cannot be stored without rounding")
    void givenUnpersistableSizeOrPrice_whenInserting_thenTheOrderIsRejectedBeforeTouchingTheRepository() {
        // Given
        NewOrderModel nullSize = validOrder().size(null).build();
        NewOrderModel zeroSize = validOrder().size(BigDecimal.ZERO).build();
        NewOrderModel fractionalSize = validOrder().size(new BigDecimal("1.5")).build();
        NewOrderModel oversizedSize = validOrder().size(new BigDecimal(Long.MAX_VALUE)).build();
        NewOrderModel nonPositivePrice = validOrder().price(BigDecimal.ZERO).build();
        NewOrderModel unroundablePrice = validOrder().price(new BigDecimal("10.001")).build();
        NewOrderModel oversizedPrice = validOrder().price(new BigDecimal("100000000")).build();

        // When
        Throwable nullSizeFailure = catchThrowable(() -> adapter.insert(nullSize));
        Throwable zeroSizeFailure = catchThrowable(() -> adapter.insert(zeroSize));
        Throwable fractionalSizeFailure = catchThrowable(() -> adapter.insert(fractionalSize));
        Throwable oversizedSizeFailure = catchThrowable(() -> adapter.insert(oversizedSize));
        Throwable nonPositivePriceFailure = catchThrowable(() -> adapter.insert(nonPositivePrice));
        Throwable unroundablePriceFailure = catchThrowable(() -> adapter.insert(unroundablePrice));
        Throwable oversizedPriceFailure = catchThrowable(() -> adapter.insert(oversizedPrice));

        // Then
        assertThat(nullSizeFailure).as("A missing size is rejected").isInstanceOf(InvalidOrderException.class);
        assertThat(zeroSizeFailure).as("A zero size is rejected").isInstanceOf(InvalidOrderException.class);
        assertThat(fractionalSizeFailure).as("A fractional size is rejected").isInstanceOf(InvalidOrderException.class);
        assertThat(oversizedSizeFailure).as("A size above the integer range is rejected").isInstanceOf(InvalidOrderException.class);
        assertThat(nonPositivePriceFailure).as("A non-positive price is rejected").isInstanceOf(InvalidOrderException.class);
        assertThat(unroundablePriceFailure).as("A price with more than two decimals is rejected").isInstanceOf(InvalidOrderException.class);
        assertThat(oversizedPriceFailure).as("A price outside the numeric column range is rejected")
                .isInstanceOf(InvalidOrderException.class);
        verifyNoInteractions(repository, entityManager);
    }

    @Test
    @DisplayName("Accepts a cash order without a price")
    void givenOrderWithoutPrice_whenInserting_thenTheOrderIsAccepted() {
        // Given
        NewOrderModel cashOrder = validOrder().price(null).build();
        OrderEntity entity = new OrderEntity();
        OrderEntity saved = new OrderEntity();
        OrderModel model = mock(OrderModel.class);
        when(mapper.toEntity(cashOrder)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toModel(saved)).thenReturn(model);

        // When
        OrderModel result = adapter.insert(cashOrder);

        // Then
        assertThat(result).as("The saved order is mapped back").isEqualTo(model);
        verify(entityManager).refresh(saved);
    }

    @Test
    @DisplayName("Persists a valid order and refreshes it from the database")
    void givenValidOrder_whenInserting_thenItIsSavedFlushedAndRefreshed() {
        // Given
        NewOrderModel newOrder = validOrder().build();
        OrderEntity entity = new OrderEntity();
        OrderEntity saved = new OrderEntity();
        OrderModel model = mock(OrderModel.class);
        when(mapper.toEntity(newOrder)).thenReturn(entity);
        when(repository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toModel(saved)).thenReturn(model);

        // When
        OrderModel result = adapter.insert(newOrder);

        // Then
        assertThat(result).as("The saved order is mapped back").isEqualTo(model);
        verify(repository).saveAndFlush(entity);
        verify(entityManager).refresh(saved);
    }

    @Test
    @DisplayName("Reports success only when the status transition is applied")
    void givenStatusTransition_whenApplied_thenTheResultReflectsTheUpdatedRowCount() {
        // Given
        when(repository.transitionStatus(1, "NEW", "FILLED")).thenReturn(1);
        when(repository.transitionStatus(2, "NEW", "FILLED")).thenReturn(0);

        // When
        boolean applied = adapter.transitionStatus(1, OrderStatusEnum.NEW, OrderStatusEnum.FILLED);
        boolean notApplied = adapter.transitionStatus(2, OrderStatusEnum.NEW, OrderStatusEnum.FILLED);

        // Then
        assertThat(applied).as("A single updated row means the transition succeeded").isTrue();
        assertThat(notApplied).as("No updated row means the transition did not apply").isFalse();
    }
}
