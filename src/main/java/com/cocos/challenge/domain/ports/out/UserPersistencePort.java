package com.cocos.challenge.domain.ports.out;

import com.cocos.challenge.domain.models.UserModel;

import java.util.Optional;

public interface UserPersistencePort {

    Optional<UserModel> findById(int userId);

    Optional<UserModel> lockById(int userId);
}
