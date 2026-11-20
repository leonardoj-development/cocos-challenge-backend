package com.cocos.challenge.infrastructure.persistence;

import com.cocos.challenge.domain.models.UserModel;
import com.cocos.challenge.infrastructure.persistence.entities.UserEntity;
import com.cocos.challenge.infrastructure.persistence.mappers.PersistenceMapper;
import com.cocos.challenge.infrastructure.persistence.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@DisplayName("User JPA adapter")
class UserJpaAdapterTest {
    private UserRepository repository;
    private PersistenceMapper mapper;
    private UserJpaAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        mapper = mock(PersistenceMapper.class);
        adapter = new UserJpaAdapter(repository, mapper);
    }

    @Test
    @DisplayName("Maps a found user by id")
    void givenExistingUser_whenFindingById_thenTheModelIsReturned() {
        // Given
        UserEntity entity = new UserEntity();
        UserModel model = UserModel.builder().id(1).email("a@example.test").accountNumber("A-1").build();
        when(repository.findById(1)).thenReturn(Optional.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        // When
        Optional<UserModel> result = adapter.findById(1);

        // Then
        assertThat(result).as("The mapped user is returned").contains(model);
    }

    @Test
    @DisplayName("Returns empty when the user does not exist")
    void givenUnknownUser_whenFindingById_thenResultIsEmpty() {
        // Given
        when(repository.findById(anyInt())).thenReturn(Optional.empty());

        // When
        Optional<UserModel> result = adapter.findById(99);

        // Then
        assertThat(result).as("No user is found").isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("Locks the user row instead of a plain read")
    void givenUserId_whenLocking_thenThePessimisticLockQueryIsUsed() {
        // Given
        UserEntity entity = new UserEntity();
        UserModel model = UserModel.builder().id(1).email("a@example.test").accountNumber("A-1").build();
        when(repository.findByIdForUpdate(1)).thenReturn(Optional.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        // When
        Optional<UserModel> result = adapter.lockById(1);

        // Then
        assertThat(result).as("The mapped user is returned").contains(model);
        verify(repository).findByIdForUpdate(1);
        verify(repository, never()).findById(anyInt());
    }
}
