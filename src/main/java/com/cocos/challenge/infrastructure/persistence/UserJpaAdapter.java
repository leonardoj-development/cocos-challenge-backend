package com.cocos.challenge.infrastructure.persistence;

import com.cocos.challenge.domain.models.UserModel;
import com.cocos.challenge.domain.ports.out.UserPersistencePort;
import com.cocos.challenge.infrastructure.persistence.mappers.PersistenceMapper;
import com.cocos.challenge.infrastructure.persistence.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserJpaAdapter implements UserPersistencePort {
    private final UserRepository repository;
    private final PersistenceMapper mapper;

    @Override
    public Optional<UserModel> findById(int userId) {
        return repository.findById(userId).map(mapper::toModel);
    }

    @Override
    public Optional<UserModel> lockById(int userId) {
        return repository.findByIdForUpdate(userId).map(mapper::toModel);
    }
}
