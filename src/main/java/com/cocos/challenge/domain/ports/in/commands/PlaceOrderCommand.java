package com.cocos.challenge.domain.ports.in.commands;

import com.cocos.challenge.domain.exceptions.InvalidRequestException;
import com.cocos.challenge.domain.models.OrderModel;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PlaceOrderCommand(int instrumentId, SideEnum side, OrderTypeEnum type, BigDecimal quantity,
                                BigDecimal amount, BigDecimal limitPrice) {
    private static final int MAX_AMOUNT_INTEGER_DIGITS = 16;
    private static final int MAX_AMOUNT_DECIMAL_PLACES = 8;

    public PlaceOrderCommand {
        if (side == null || type == null) {
            throw new InvalidRequestException("side and type are required");
        }
        if (instrumentId <= 0) {
            throw new InvalidRequestException("instrumentId must be positive");
        }
        if (!SideEnum.BUY.equals(side) && !SideEnum.SELL.equals(side)) {
            throw new InvalidRequestException("side not accepted: " + side);
        }
        if ((quantity == null) == (amount == null)) {
            throw new InvalidRequestException("exactly one of quantity or amount is required");
        }
        if (quantity != null && (quantity.signum() <= 0 || !OrderModel.isWholeShareQuantity(quantity))) {
            throw new InvalidRequestException("quantity must be a positive whole number");
        }
        if (amount != null && (amount.signum() <= 0 || !isAmountWithinAcceptedPrecision(amount))) {
            throw new InvalidRequestException("amount must be positive and within accepted precision");
        }
        if (OrderTypeEnum.LIMIT.equals(type)) {
            if (limitPrice == null || limitPrice.signum() <= 0) {
                throw new InvalidRequestException("limitPrice must be positive");
            }
        } else if (limitPrice != null) {
            throw new InvalidRequestException("limitPrice must be absent for MARKET orders");
        }
    }

    public static boolean isAmountWithinAcceptedPrecision(BigDecimal amount) {
        if (amount.signum() == 0) {
            return true;
        }
        return (long) amount.precision() - amount.scale() <= MAX_AMOUNT_INTEGER_DIGITS
                && (amount.scale() <= MAX_AMOUNT_DECIMAL_PLACES
                || amount.stripTrailingZeros().scale() <= MAX_AMOUNT_DECIMAL_PLACES);
    }
}
