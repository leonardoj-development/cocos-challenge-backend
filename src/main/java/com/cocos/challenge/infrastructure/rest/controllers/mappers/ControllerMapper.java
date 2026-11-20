package com.cocos.challenge.infrastructure.rest.controllers.mappers;

import com.cocos.challenge.domain.models.*;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.infrastructure.rest.controllers.dtos.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;

import java.util.List;

@Mapper(componentModel = ComponentModel.SPRING)
public interface ControllerMapper {
    @Mapping(target = "instrumentId", source = "instrumentId", defaultValue = "0")
    PlaceOrderCommand toCommand(PlaceOrderRequestDto request);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "userId", source = "order.userId")
    @Mapping(target = "instrumentId", source = "order.instrumentId")
    @Mapping(target = "side", source = "order.side")
    @Mapping(target = "type", source = "order.type")
    @Mapping(target = "quantity", source = "order.size")
    @Mapping(target = "price", source = "order.price")
    @Mapping(target = "status", source = "order.status")
    @Mapping(target = "datetime", source = "order.datetime")
    @Mapping(target = "rejectionReason", source = "rejectionReason")
    OrderResponseDto toOrderResponse(PlaceOrderResultModel result);

    CancelOrderResponseDto toCancelOrderResponse(OrderModel order);

    @Mapping(target = "items", source = "instruments")
    InstrumentSearchResponseDto toInstrumentSearchResponse(List<InstrumentModel> instruments, int limit, int offset);

    InstrumentResponseDto toInstrumentResponse(InstrumentModel instrument);

    @Mapping(target = "positions", source = "positions")
    PortfolioResponseDto toPortfolioResponse(PortfolioModel portfolio);

    PositionResponseDto toPositionResponse(PortfolioPositionModel position);
}
