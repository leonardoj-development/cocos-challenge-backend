package com.cocos.challenge.infrastructure.rest.controllers.validators;

import jakarta.validation.ConstraintDeclarationException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import java.util.Arrays;
import java.util.List;

public class EnumValueValidator implements ConstraintValidator<ValidEnum, String> {
    private List<String> values;

    @Override
    public void initialize(ValidEnum constraint) {
        List<String> enumValues = Arrays.stream(constraint.enumClass().getEnumConstants())
                .map(Enum::name)
                .toList();
        values = constraint.allowedValues().length == 0 ? enumValues : List.of(constraint.allowedValues());
        if (!enumValues.containsAll(values)) {
            throw new ConstraintDeclarationException("Allowed values must belong to " + constraint.enumClass().getName());
        }
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || values.contains(value)) {
            return true;
        }
        context.unwrap(HibernateConstraintValidatorContext.class)
                .addMessageParameter("values", String.join(", ", values));
        return false;
    }
}
