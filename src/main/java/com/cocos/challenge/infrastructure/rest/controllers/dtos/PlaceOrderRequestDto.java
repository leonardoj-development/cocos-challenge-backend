package com.cocos.challenge.infrastructure.rest.controllers.dtos;

import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.infrastructure.rest.controllers.validators.ValidEnum;
import com.cocos.challenge.infrastructure.rest.controllers.validators.ValidOrder;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidOrder
public class PlaceOrderRequestDto {
    @NotNull(message = "instrumentId is required")
    @Positive(message = "instrumentId must be positive")
    private Integer instrumentId;
    @NotNull(message = "side is required")
    @ValidEnum(enumClass = SideEnum.class, allowedValues = {"BUY", "SELL"},
            message = "side must be one of: {values}")
    private String side;
    @NotNull(message = "type is required")
    @ValidEnum(enumClass = OrderTypeEnum.class, message = "type must be one of: {values}")
    private String type;
    @Positive(message = "quantity must be positive")
    @DecimalMax(value = "2147483647", message = "quantity exceeds the maximum of 2147483647")
    private BigDecimal quantity;
    @Positive(message = "amount must be positive")
    private BigDecimal amount;
    @Positive(message = "limitPrice must be positive")
    @DecimalMax(value = "99999999.99", message = "limitPrice exceeds the maximum of 99999999.99")
    private BigDecimal limitPrice;
}
