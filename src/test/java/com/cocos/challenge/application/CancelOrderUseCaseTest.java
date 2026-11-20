package com.cocos.challenge.application;

import com.cocos.challenge.domain.exceptions.OrderNotCancellableException;
import com.cocos.challenge.domain.exceptions.OrderNotFoundException;
import com.cocos.challenge.domain.exceptions.UserNotFoundException;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.UserModel;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.out.OrderPersistencePort;
import com.cocos.challenge.domain.ports.out.UserPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@DisplayName("Cancel order use case")
class CancelOrderUseCaseTest {
    private static final int USER_ID = 1;
    private static final int ORDER_ID = 2;

    private UserPersistencePort users;
    private OrderPersistencePort orders;
    private CancelOrderUseCase useCase;

    private static OrderModel order(OrderStatusEnum status) {
        return OrderModel.builder()
                .id(ORDER_ID)
                .userId(USER_ID)
                .instrumentId(3)
                .side(SideEnum.BUY)
                .size(new BigDecimal("5"))
                .price(new BigDecimal("100"))
                .status(status)
                .datetime(Instant.now())
                .build();
    }

    @BeforeEach
    void setUp() {
        users = mock(UserPersistencePort.class);
        orders = mock(OrderPersistencePort.class);
        useCase = new CancelOrderUseCase(users, orders);

        when(users.lockById(anyInt())).thenReturn(Optional.empty());
        when(users.lockById(USER_ID)).thenReturn(Optional.of(UserModel.builder().id(USER_ID).build()));
    }

    @Test
    @DisplayName("Cancels a new order")
    void givenNewOrder_whenCancelled_thenStatusChangesToCancelled() {
        // Given
        OrderModel newOrder = order(OrderStatusEnum.NEW);
        OrderModel cancelledOrder = order(OrderStatusEnum.CANCELLED);
        when(orders.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(Optional.of(newOrder), Optional.of(cancelledOrder));
        when(orders.transitionStatus(ORDER_ID, OrderStatusEnum.NEW, OrderStatusEnum.CANCELLED)).thenReturn(true);

        // When
        OrderModel result = useCase.execute(USER_ID, ORDER_ID);

        // Then
        assertThat(result.status()).as("The returned order is cancelled").isEqualTo(OrderStatusEnum.CANCELLED);
        verify(users, times(1)).lockById(USER_ID);
        verify(orders).transitionStatus(ORDER_ID, OrderStatusEnum.NEW, OrderStatusEnum.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatusEnum.class, names = {"FILLED", "REJECTED", "CANCELLED"})
    @DisplayName("Rejects cancellation of orders that are not new")
    void givenNonNewOrder_whenCancelled_thenStatusIsUnchanged(OrderStatusEnum status) {
        // Given
        when(orders.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(Optional.of(order(status)));

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, ORDER_ID));

        // Then
        assertThat(failure).as("A non-new order cannot be cancelled").isInstanceOf(OrderNotCancellableException.class);
        verify(orders, never()).transitionStatus(anyInt(), any(), any());
    }

    @Test
    @DisplayName("Reports the order as not found when it disappears after the cancellation")
    void givenOrderRemovedAfterTransition_whenCancelled_thenOrderIsNotFound() {
        // Given
        when(orders.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(Optional.of(order(OrderStatusEnum.NEW)),
                Optional.empty());
        when(orders.transitionStatus(ORDER_ID, OrderStatusEnum.NEW, OrderStatusEnum.CANCELLED)).thenReturn(true);

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, ORDER_ID));

        // Then
        assertThat(failure).as("The reloaded order must exist").isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Hides another user's order and reports missing orders")
    void givenOtherUsersOrMissingOrder_whenCancelled_thenOrderIsNotFound() {
        // Given
        when(users.lockById(2)).thenReturn(Optional.of(UserModel.builder().id(2).build()));
        when(orders.findByIdAndUser(ORDER_ID, 2)).thenReturn(Optional.empty());
        when(orders.findByIdAndUser(99, USER_ID)).thenReturn(Optional.empty());

        // When
        Throwable otherUserFailure = catchThrowable(() -> useCase.execute(2, ORDER_ID));
        Throwable missingFailure = catchThrowable(() -> useCase.execute(USER_ID, 99));

        // Then
        assertThat(otherUserFailure).as("Another user's order is not exposed").isInstanceOf(OrderNotFoundException.class);
        assertThat(missingFailure).as("A missing order is not found").isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("Reports an unknown user")
    void givenUnknownUser_whenCancellingOrder_thenUserIsNotFound() {
        // Given
        int unknownUserId = 99;

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(unknownUserId, 1));

        // Then
        assertThat(failure).as("The unknown user is not found").isInstanceOf(UserNotFoundException.class);
        verify(orders, never()).findByIdAndUser(anyInt(), anyInt());
    }

    @Test
    @DisplayName("Reports a lost conditional update as not cancellable")
    void givenLostConditionalUpdate_whenCancellingOrder_thenOrderIsNotCancellable() {
        // Given
        when(orders.findByIdAndUser(ORDER_ID, USER_ID)).thenReturn(Optional.of(order(OrderStatusEnum.NEW)));
        when(orders.transitionStatus(ORDER_ID, OrderStatusEnum.NEW, OrderStatusEnum.CANCELLED)).thenReturn(false);

        // When
        Throwable failure = catchThrowable(() -> useCase.execute(USER_ID, ORDER_ID));

        // Then
        assertThat(failure).as("A failed status transition cannot be cancelled")
                .isInstanceOf(OrderNotCancellableException.class);
    }
}
