package com.cocos.challenge.infrastructure.rest.controllers.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = OrderRequestValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidOrder {
    String message() default "Invalid order";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
