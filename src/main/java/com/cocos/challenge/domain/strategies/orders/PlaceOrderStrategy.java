package com.cocos.challenge.domain.strategies.orders;

import com.cocos.challenge.domain.exceptions.InvalidOrderException;
import com.cocos.challenge.domain.models.PlaceOrderDecisionModel;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;

import java.math.BigDecimal;
import java.math.RoundingMode;

public interface PlaceOrderStrategy {

    boolean supports(SideEnum side, OrderTypeEnum type);

    /**
     * @param marketClose latest close of the instrument; only provided for MARKET orders
     */
    PlaceOrderDecisionModel apply(PlaceOrderCommand command, BigDecimal marketClose);

    /**
     * Shared by every strategy. Kept as a public {@code default} method on purpose: it is a single pure
     * function, so a base class (or a separate bean) would add a hierarchy just for one method.
     * Trade-off: it becomes part of the public contract of each strategy.
     */
    default BigDecimal resolveQuantity(PlaceOrderCommand command, BigDecimal price) {
        if (command.quantity() != null) {
            return command.quantity();
        }
        BigDecimal amount = command.amount();
        if (amount.signum() <= 0 || price.signum() <= 0) {
            throw new InvalidOrderException("amount and price must be positive");
        }
        BigDecimal quantity = amount.divideToIntegralValue(price).setScale(0, RoundingMode.DOWN);
        if (quantity.signum() == 0) {
            throw new InvalidOrderException("amount is lower than the price of one share");
        }
        return quantity;
    }
}
