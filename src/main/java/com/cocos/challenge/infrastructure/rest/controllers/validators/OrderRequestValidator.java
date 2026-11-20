package com.cocos.challenge.infrastructure.rest.controllers.validators;

import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.infrastructure.rest.controllers.dtos.PlaceOrderRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class OrderRequestValidator implements ConstraintValidator<ValidOrder, PlaceOrderRequestDto> {
    private static void violation(ConstraintValidatorContext context, String field, String message) {
        var violation = context.buildConstraintViolationWithTemplate(message);
        if (field == null) {
            violation.addConstraintViolation();
        } else {
            violation.addPropertyNode(field).addConstraintViolation();
        }
    }

    @Override
    public boolean isValid(PlaceOrderRequestDto order, ConstraintValidatorContext context) {
        if (order == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        BigDecimal quantity = order.getQuantity();
        BigDecimal amount = order.getAmount();
        BigDecimal limitPrice = order.getLimitPrice();
        if ((quantity == null) == (amount == null)) {
            violation(context, null, "exactly one of quantity or amount is required");
            valid = false;
        }
        if (quantity != null && !OrderModel.isWholeShareQuantity(quantity)) {
            violation(context, "quantity", "quantity must be a whole number");
            valid = false;
        }
        if (amount != null && !PlaceOrderCommand.isAmountWithinAcceptedPrecision(amount)) {
            violation(context, "amount", "amount must have at most 16 integer digits and 8 decimal places");
            valid = false;
        }
        if (OrderTypeEnum.LIMIT.name().equals(order.getType()) && limitPrice == null) {
            violation(context, "limitPrice", "limitPrice is required for LIMIT orders");
            valid = false;
        } else if (OrderTypeEnum.MARKET.name().equals(order.getType()) && limitPrice != null) {
            violation(context, "limitPrice", "limitPrice must be absent for MARKET orders");
            valid = false;
        }
        if (limitPrice != null && limitPrice.scale() > 2 && limitPrice.stripTrailingZeros().scale() > 2) {
            violation(context, "limitPrice", "limitPrice must have at most 2 decimal places");
            valid = false;
        }
        return valid;
    }
}
