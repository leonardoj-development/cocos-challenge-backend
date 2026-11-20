package com.cocos.challenge.infrastructure.rest.controllers.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = EnumValueValidator.class)
@Target(FIELD)
@Retention(RUNTIME)
public @interface ValidEnum {
    Class<? extends Enum<?>> enumClass();

    String[] allowedValues() default {};

    String message() default "must be one of: {values}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
