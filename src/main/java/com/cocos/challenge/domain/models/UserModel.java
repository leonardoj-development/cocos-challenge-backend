package com.cocos.challenge.domain.models;

import lombok.Builder;

@Builder
public record UserModel(int id, String email, String accountNumber) {
}
