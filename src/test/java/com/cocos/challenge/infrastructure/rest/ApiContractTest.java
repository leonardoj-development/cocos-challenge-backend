package com.cocos.challenge.infrastructure.rest;

import com.cocos.challenge.domain.ports.in.queries.SearchInstrumentsQuery;
import com.cocos.challenge.domain.exceptions.*;
import com.cocos.challenge.domain.models.*;
import com.cocos.challenge.domain.models.enums.OrderStatusEnum;
import com.cocos.challenge.domain.models.enums.OrderTypeEnum;
import com.cocos.challenge.domain.models.enums.RejectionReasonEnum;
import com.cocos.challenge.domain.models.enums.SideEnum;
import com.cocos.challenge.domain.ports.in.CancelOrderPort;
import com.cocos.challenge.domain.ports.in.GetPortfolioPort;
import com.cocos.challenge.domain.ports.in.PlaceOrderPort;
import com.cocos.challenge.domain.ports.in.SearchInstrumentsPort;
import com.cocos.challenge.domain.ports.in.commands.PlaceOrderCommand;
import com.cocos.challenge.infrastructure.config.JacksonConfig;
import com.cocos.challenge.infrastructure.rest.controllers.InstrumentController;
import com.cocos.challenge.infrastructure.rest.controllers.OrderController;
import com.cocos.challenge.infrastructure.rest.controllers.PortfolioController;
import com.cocos.challenge.infrastructure.rest.controllers.dtos.PlaceOrderRequestDto;
import com.cocos.challenge.infrastructure.rest.controllers.mappers.ControllerMapperImpl;
import com.cocos.challenge.infrastructure.rest.exceptions.GlobalExceptionHandler;
import jakarta.validation.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static com.cocos.challenge.infrastructure.rest.ResponseAssertions.described;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({OrderController.class, PortfolioController.class, InstrumentController.class})
@Import({GlobalExceptionHandler.class, ControllerMapperImpl.class, JacksonConfig.class})
@DisplayName("REST API contract")
class ApiContractTest {
    private static final String ORDERS = "/api/v1/users/1/orders";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00.000Z");
    private static final String VALID_BODY =
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":10,\"limitPrice\":80.00000000}";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PlaceOrderPort placeOrder;
    @MockitoBean
    private CancelOrderPort cancelOrder;
    @MockitoBean
    private GetPortfolioPort getPortfolio;
    @MockitoBean
    private SearchInstrumentsPort searchInstruments;

    private static OrderModel order(OrderStatusEnum status) {
        return OrderModel.builder().id(1).userId(1).instrumentId(2).side(SideEnum.BUY)
                .size(new BigDecimal("10.00000000")).price(new BigDecimal("80.00000000"))
                .type(OrderTypeEnum.LIMIT).status(status).datetime(NOW).build();
    }

    static Stream<Arguments> domainErrors() {
        return Stream.of(
                Arguments.of(new InvalidRequestException("bad"), 400, "INVALID_REQUEST"),
                Arguments.of(new UserNotFoundException("u"), 404, "USER_NOT_FOUND"),
                Arguments.of(new InstrumentNotFoundException("i"), 404, "INSTRUMENT_NOT_FOUND"),
                Arguments.of(new OrderNotFoundException("o"), 404, "ORDER_NOT_FOUND"),
                Arguments.of(new InvalidOrderException("x"), 422, "INVALID_ORDER"),
                Arguments.of(new OrderNotCancellableException("c"), 409, "ORDER_NOT_CANCELLABLE"),
                Arguments.of(new MarketDataUnavailableException("m"), 409, "MARKET_DATA_UNAVAILABLE"),
                Arguments.of(new InconsistentDataException("secret detail"), 500, "INCONSISTENT_DATA"),
                Arguments.of(new IllegalStateException("jdbc:postgresql://secret"), 500, "INTERNAL_ERROR"));
    }

    static Stream<Arguments> invalidOrderBodyMessages() {
        return Stream.of(
                Arguments.of("{\"instrumentId\":41,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":\"2\"}",
                        "quantity must be a JSON number (without quotes)"),
                Arguments.of("{\"quantity\":true}", "quantity must be a JSON number (without quotes)"),
                Arguments.of("{\"amount\":[]}", "amount must be a JSON number (without quotes)"),
                Arguments.of("{\"limitPrice\":\"10\"}", "limitPrice must be a JSON number (without quotes)"),
                Arguments.of("{\"instrumentId\":\"2\"}", "instrumentId must be a 32-bit integer JSON number (without quotes)"),
                Arguments.of("{\"instrumentId\":2.5}", "instrumentId must be a 32-bit integer JSON number (without quotes)"),
                Arguments.of("{\"secret-field\":1}", "Request body contains an unknown field"),
                Arguments.of("{", "Malformed or invalid JSON request body"),
                Arguments.of("[]", "Malformed or invalid JSON request body"));
    }

    static Stream<Arguments> invalidOrderValues() {
        return Stream.of(
                Arguments.of("{\"instrumentId\":0,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}", "instrumentId must be positive"),
                Arguments.of("{\"instrumentId\":1,\"type\":\"MARKET\",\"quantity\":1}", "side is required"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"quantity\":1}", "type is required"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"CASH_IN\",\"type\":\"MARKET\",\"quantity\":1}", "side must be one of: BUY, SELL"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"CASH_OUT\",\"type\":\"MARKET\",\"quantity\":1}", "side must be one of: BUY, SELL"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"buy\",\"type\":\"MARKET\",\"quantity\":1}", "side must be one of: BUY, SELL"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY \",\"type\":\"MARKET\",\"quantity\":1}", "side must be one of: BUY, SELL"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"STOP\",\"quantity\":1}", "type must be one of: MARKET, LIMIT"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"0\",\"quantity\":1}", "type must be one of: MARKET, LIMIT"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"secret-value\",\"quantity\":1}", "type must be one of: MARKET, LIMIT"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"market\",\"quantity\":1}", "type must be one of: MARKET, LIMIT"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"\",\"type\":\"MARKET\",\"quantity\":1}", "side must be one of: BUY, SELL"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"\",\"quantity\":1}", "type must be one of: MARKET, LIMIT"),
                Arguments.of("{\"instrumentId\":1,\"side\":true,\"type\":\"MARKET\",\"quantity\":1}", "side must be one of: BUY, SELL"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":0,\"quantity\":1}", "type must be one of: MARKET, LIMIT"),
                Arguments.of("{\"instrumentId\":1,\"side\":null,\"type\":\"MARKET\",\"quantity\":1}", "side is required"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":null,\"quantity\":1}", "type is required"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\"}", "exactly one of quantity or amount is required"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1,\"amount\":1}", "exactly one of quantity or amount is required"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":0}", "quantity must be positive"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1.5}", "quantity must be a whole number"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":2147483648}", "quantity exceeds the maximum of 2147483647"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":-1}", "amount must be positive"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":0.000000001}", "amount must have at most 16 integer digits and 8 decimal places"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":10000000000000000}", "amount must have at most 16 integer digits and 8 decimal places"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1}", "limitPrice is required for LIMIT orders"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1,\"limitPrice\":1}", "limitPrice must be absent for MARKET orders"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"limitPrice\":0}", "limitPrice must be positive"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"limitPrice\":1.001}", "limitPrice must have at most 2 decimal places"),
                Arguments.of("{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"limitPrice\":100000000}", "limitPrice exceeds the maximum of 99999999.99"));
    }

    static Stream<Arguments> validOrderEnums() {
        return Stream.of(
                Arguments.of("BUY", "MARKET"),
                Arguments.of("SELL", "MARKET"),
                Arguments.of("BUY", "LIMIT"),
                Arguments.of("SELL", "LIMIT"));
    }

    private MockHttpServletRequestBuilder postOrder(String body) {
        return post(ORDERS).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    @DisplayName("Returns a created order with numeric decimals and no rejection reason")
    void givenValidOrder_whenPlacingOrder_thenResponseContainsNumericValues() throws Exception {
        // Given
        when(placeOrder.execute(eq(1), any())).thenReturn(PlaceOrderResultModel.builder()
                .order(order(OrderStatusEnum.NEW)).build());

        // When
        var response = mvc.perform(postOrder(VALID_BODY));

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.id value 1", jsonPath("$.id").value(1)))
                .andExpect(described("JSON $.userId value 1", jsonPath("$.userId").value(1)))
                .andExpect(described("JSON $.instrumentId value 2", jsonPath("$.instrumentId").value(2)))
                .andExpect(described("JSON $.side value \"BUY\"", jsonPath("$.side").value("BUY")))
                .andExpect(described("JSON $.type value \"LIMIT\"", jsonPath("$.type").value("LIMIT")))
                .andExpect(described("JSON $.quantity isNumber", jsonPath("$.quantity").isNumber()))
                .andExpect(described("JSON $.quantity value 10", jsonPath("$.quantity").value(10)))
                .andExpect(described("JSON $.price isNumber", jsonPath("$.price").isNumber()))
                .andExpect(described("JSON $.price value 80.00", jsonPath("$.price").value(80.00)))
                .andExpect(described("JSON $.status value \"NEW\"", jsonPath("$.status").value("NEW")))
                .andExpect(described("JSON $.datetime value \"2026-01-01T12:00:00.000Z\"", jsonPath("$.datetime").value("2026-01-01T12:00:00.000Z")))
                .andExpect(described("JSON $.rejectionReason hasJsonPath", jsonPath("$.rejectionReason").hasJsonPath()))
                .andExpect(described("JSON $.rejectionReason value org.hamcrest.Matchers.nullValue()", jsonPath("$.rejectionReason").value(org.hamcrest.Matchers.nullValue())));
        response.andExpect(content().string(org.hamcrest.Matchers.containsString("\"price\":80.00,")));

        ArgumentCaptor<PlaceOrderCommand> captor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(placeOrder, description("The order port receives the mapped request")).execute(eq(1), captor.capture());
        assertThat(captor.getValue()).as("The request is mapped to the expected command")
                .isEqualTo(PlaceOrderCommand.builder().instrumentId(2).side(SideEnum.BUY).type(OrderTypeEnum.LIMIT)
                        .quantity(new BigDecimal("10")).limitPrice(new BigDecimal("80.00000000")).build());
    }

    @Test
    @DisplayName("Accepts standard JSON numbers with exponents")
    void givenExponentNumbers_whenPlacingOrder_thenValuesAreDeserialized() throws Exception {
        // Given
        when(placeOrder.execute(eq(1), any())).thenReturn(PlaceOrderResultModel.builder()
                .order(order(OrderStatusEnum.NEW)).build());

        // When
        var response = mvc.perform(postOrder("{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"LIMIT\","
                + "\"quantity\":1e1,\"limitPrice\":8E+1}"));

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()));

        ArgumentCaptor<PlaceOrderCommand> captor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(placeOrder, description("The order port receives exponent values")).execute(eq(1), captor.capture());
        assertThat(captor.getValue().quantity()).as("Quantity exponent is parsed").isEqualByComparingTo("10");
        assertThat(captor.getValue().limitPrice()).as("Price exponent is parsed").isEqualByComparingTo("80");

        mvc.perform(postOrder("{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":2.5e-2}"))
                .andExpect(status().isCreated());
        verify(placeOrder, times(2)).execute(eq(1), captor.capture());
        assertThat(captor.getAllValues().getLast().amount()).as("Amount exponent is parsed").isEqualByComparingTo("0.025");
    }

    @Test
    @DisplayName("Returns a created rejected order with its reason")
    void givenRejectedOrder_whenPlacingOrder_thenResponseContainsRejectionReason() throws Exception {
        // Given
        when(placeOrder.execute(eq(1), any())).thenReturn(
                PlaceOrderResultModel.builder().order(order(OrderStatusEnum.REJECTED))
                        .rejectionReason(RejectionReasonEnum.INSUFFICIENT_FUNDS).build());

        // When
        var response = mvc.perform(postOrder(VALID_BODY));

        // Then
        response
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.status value \"REJECTED\"", jsonPath("$.status").value("REJECTED")))
                .andExpect(described("JSON $.rejectionReason value \"INSUFFICIENT_FUNDS\"", jsonPath("$.rejectionReason").value("INSUFFICIENT_FUNDS")));
    }

    @Test
    @DisplayName("Rejects a missing instrument id before invoking the domain")
    void givenMissingInstrumentId_whenPlacingOrder_thenValidationReportsInvalidOrder() throws Exception {
        // When
        var response = mvc.perform(postOrder("{\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}"));

        // Then
        response
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()))
                .andExpect(described("JSON $.code value \"INVALID_ORDER\"", jsonPath("$.code").value("INVALID_ORDER")))
                .andExpect(jsonPath("$.message").value("instrumentId is required"));
        verifyNoInteractions(placeOrder);
    }

    @ParameterizedTest
    @MethodSource("domainErrors")
    @DisplayName("Maps exceptions without exposing internal details")
    void givenDomainOrInternalFailure_whenPlacingOrder_thenErrorIsMappedWithoutLeaks(RuntimeException error, int httpStatus, String code) throws Exception {
        // Given
        when(placeOrder.execute(anyInt(), any())).thenThrow(error);

        // When
        var response = mvc.perform(postOrder(VALID_BODY));

        // Then
        response
                .andExpect(described("HTTP status is httpStatus", status().is(httpStatus)))
                .andExpect(described("JSON $.code value code", jsonPath("$.code").value(code)))
                .andExpect(described("JSON $.message isString", jsonPath("$.message").isString()))
                .andExpect(described("Response content matches the expected contract", content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret")))));
    }

    @Test
    @DisplayName("Domain exception types preserve message and cause")
    void givenCauseAndMessage_whenConstructingDomainExceptions_thenBothArePreserved() {
        // Given
        Throwable cause = new IllegalStateException("cause");

        // When
        List<RuntimeException> exceptions = List.of(
                new InvalidRequestException("message", cause),
                new UserNotFoundException("message", cause),
                new InstrumentNotFoundException("message", cause),
                new OrderNotFoundException("message", cause),
                new InvalidOrderException("message", cause),
                new OrderNotCancellableException("message", cause),
                new MarketDataUnavailableException("message", cause),
                new InconsistentDataException("message", cause));

        // Then
        assertThat(exceptions).as("Each concrete exception preserves its details").allSatisfy(ex -> {
            assertThat(ex.getClass().getSuperclass()).as("The exception directly extends RuntimeException").isEqualTo(RuntimeException.class);
            assertThat(ex.getMessage()).as("The message is preserved").isEqualTo("message");
            assertThat(ex.getCause()).as("The cause is preserved").isSameAs(cause);
        });
    }

    @Test
    @DisplayName("Does not expose an exception cause or change its HTTP mapping")
    void givenDomainExceptionWithCause_whenPlacingOrder_thenMappingDoesNotExposeCause() throws Exception {
        // Given
        when(placeOrder.execute(anyInt(), any())).thenThrow(
                new InvalidOrderException("Invalid order", new IllegalStateException("secret")));

        // When
        var response = mvc.perform(postOrder(VALID_BODY));

        // Then
        response
                .andExpect(described("HTTP status isUnprocessableEntity", status().isUnprocessableEntity()))
                .andExpect(described("JSON $.code value \"INVALID_ORDER\"", jsonPath("$.code").value("INVALID_ORDER")))
                .andExpect(described("JSON $.message value \"Invalid order\"", jsonPath("$.message").value("Invalid order")))
                .andExpect(described("Response content matches the expected contract", content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret")))));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{",
            "[]",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1,\"extra\":1}",
            "{\"instrumentId\":2,\"instrumentId\":3,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}",
            "{\"instrumentId\":\"2\",\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}",
            "{\"instrumentId\":2.5,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1}",
            "{\"instrumentId\":2,\"side\":[],\"type\":\"MARKET\",\"quantity\":1}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":{},\"quantity\":1}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":\"1\"}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":\"1e1\"}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":\"+1\"}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":\"abc\"}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":\"10.5\"}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":1,\"limitPrice\":\"10\"}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":true}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":[]}",
            "{\"instrumentId\":2,\"side\":\"BUY\",\"type\":\"MARKET\",\"quantity\":1} {}"})
    @DisplayName("Rejects malformed request bodies before invoking the domain")
    void givenMalformedBody_whenPlacingOrder_thenRequestIsRejectedBeforeDomain(String body) throws Exception {
        // Given
        MockHttpServletRequestBuilder request = postOrder(body);

        // When
        var response = mvc.perform(request);

        // Then
        response
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest()))
                .andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));

        assertThatCode(() -> verifyNoInteractions(placeOrder)).as("Malformed requests do not reach the order port")
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("invalidOrderBodyMessages")
    @DisplayName("Explains invalid order request fields without exposing input")
    void givenInvalidOrderBody_whenPlacingOrder_thenResponseExplainsTheError(String body, String message)
            throws Exception {
        mvc.perform(postOrder(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-field"))));
        verifyNoInteractions(placeOrder);
    }

    @ParameterizedTest
    @MethodSource("invalidOrderValues")
    void givenInvalidOrderValues_whenPlacingOrder_thenValidationReportsSpecificMessage(String body, String message)
            throws Exception {
        mvc.perform(postOrder(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(message)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-value"))));
        verifyNoInteractions(placeOrder);
    }

    @ParameterizedTest
    @MethodSource("validOrderEnums")
    void givenValidOrderEnums_whenPlacingOrder_thenMapperConvertsValues(String side, String type) throws Exception {
        when(placeOrder.execute(eq(1), any())).thenReturn(PlaceOrderResultModel.builder().order(order(OrderStatusEnum.NEW)).build());
        String price = type.equals("LIMIT") ? ",\"limitPrice\":1" : "";
        mvc.perform(postOrder("{\"instrumentId\":1,\"side\":\"" + side + "\",\"type\":\"" + type
                + "\",\"quantity\":1" + price + "}")).andExpect(status().isCreated());

        ArgumentCaptor<PlaceOrderCommand> captor = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(placeOrder).execute(eq(1), captor.capture());
        assertThat(captor.getValue().side()).isEqualTo(SideEnum.valueOf(side));
        assertThat(captor.getValue().type()).isEqualTo(OrderTypeEnum.valueOf(type));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"LIMIT\",\"quantity\":2147483647.000,\"limitPrice\":99999999.9900}",
            "{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":9999999999999999.1234567800}",
            "{\"instrumentId\":1,\"side\":\"BUY\",\"type\":\"MARKET\",\"amount\":0.00000001}"})
    void givenValidBoundariesAndTrailingZeros_whenPlacingOrder_thenRequestReachesDomain(String body) throws Exception {
        when(placeOrder.execute(eq(1), any())).thenReturn(PlaceOrderResultModel.builder().order(order(OrderStatusEnum.NEW)).build());
        mvc.perform(postOrder(body)).andExpect(status().isCreated());
        verify(placeOrder).execute(eq(1), any());
    }

    @Test
    void givenMultipleInvalidFields_whenPlacingOrder_thenAllValidationMessagesAreReturned() throws Exception {
        mvc.perform(postOrder("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("exactly one of quantity or amount is required; "
                        + "instrumentId is required; side is required; type is required"));
        verifyNoInteractions(placeOrder);
    }

    @Test
    void givenInvalidBodyWithoutMethodConstraints_whenValidated_thenAllMessagesAreReturned() throws Exception {
        try (LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            MockMvc standalone = MockMvcBuilders.standaloneSetup(new BodyOnlyController())
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setValidator(validator)
                    .build();
            standalone.perform(post("/validate-order").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("INVALID_ORDER"))
                    .andExpect(jsonPath("$.message").value("exactly one of quantity or amount is required; "
                            + "instrumentId is required; side is required; type is required"));
        }
    }

    @Test
    @DisplayName("Rejects invalid path identifiers")
    void givenInvalidPathIds_whenCallingEndpoints_thenRequestsAreRejected() throws Exception {
        // Given
        String invalidUserPath = "/api/v1/users/0/orders";

        // When
        var response = mvc.perform(post(invalidUserPath).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));

        // Then
        response
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest())).andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
        mvc.perform(post("/api/v1/users/abc/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest())).andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
        mvc.perform(post(invalidUserPath).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/users/1/orders/-1/cancel"))
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest())).andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
        mvc.perform(get("/api/v1/users/0/portfolio"))
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest())).andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));

        assertThatCode(() -> verifyNoInteractions(placeOrder, cancelOrder, getPortfolio))
                .as("Invalid path ids do not reach the domain ports").doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Returns the cancelled order id and status")
    void givenCancellableOrder_whenCancelling_thenResponseContainsIdAndStatus() throws Exception {
        // Given
        when(cancelOrder.execute(1, 7)).thenReturn(
                OrderModel.builder().id(7).userId(1).instrumentId(2).side(SideEnum.BUY)
                        .size(BigDecimal.TEN).price(BigDecimal.ONE).type(OrderTypeEnum.LIMIT)
                        .status(OrderStatusEnum.CANCELLED).datetime(NOW).build());

        // When
        var response = mvc.perform(post("/api/v1/users/1/orders/7/cancel"));

        // Then
        response
                .andExpect(described("HTTP status isOk", status().isOk()))
                .andExpect(described("JSON $.id value 7", jsonPath("$.id").value(7)))
                .andExpect(described("JSON $.status value \"CANCELLED\"", jsonPath("$.status").value("CANCELLED")))
                .andExpect(described("JSON $.length() value 2", jsonPath("$.length()").value(2)));
    }

    @Test
    @DisplayName("Maps cancellation errors")
    void givenUncancellableOrMissingOrder_whenCancelling_thenErrorsAreMapped() throws Exception {
        // Given
        when(cancelOrder.execute(1, 7)).thenThrow(new OrderNotCancellableException("no"));
        when(cancelOrder.execute(1, 8)).thenThrow(new OrderNotFoundException("no"));

        // When
        var response = mvc.perform(post("/api/v1/users/1/orders/7/cancel"));

        // Then
        response
                .andExpect(described("HTTP status isConflict", status().isConflict())).andExpect(described("JSON $.code value \"ORDER_NOT_CANCELLABLE\"", jsonPath("$.code").value("ORDER_NOT_CANCELLABLE")));
        mvc.perform(post("/api/v1/users/1/orders/8/cancel"))
                .andExpect(described("HTTP status isNotFound", status().isNotFound())).andExpect(described("JSON $.code value \"ORDER_NOT_FOUND\"", jsonPath("$.code").value("ORDER_NOT_FOUND")));
    }

    @Test
    @DisplayName("Serializes portfolio decimals as JSON numbers")
    void givenPortfolioWithPosition_whenGettingPortfolio_thenDecimalsAreNumbers() throws Exception {
        // Given
        PortfolioPositionModel position = PortfolioPositionModel.builder().instrumentId(2).ticker("AAA")
                .name("Activo A").quantity(new BigDecimal("40.00000000")).marketPrice(new BigDecimal("80"))
                .marketValue(new BigDecimal("800")).totalReturnPercent(new BigDecimal("14.2857"))
                .dailyReturnPercent(new BigDecimal("6.6667")).build();
        when(getPortfolio.execute(1)).thenReturn(PortfolioModel.builder().userId(1).currency("ARS")
                .totalValue(new BigDecimal("1200")).availableCash(new BigDecimal("400"))
                .positions(List.of(position)).build());

        // When
        var response = mvc.perform(get("/api/v1/users/1/portfolio"));

        // Then
        response
                .andExpect(described("HTTP status isOk", status().isOk()))
                .andExpect(described("JSON $.userId value 1", jsonPath("$.userId").value(1)))
                .andExpect(described("JSON $.currency value \"ARS\"", jsonPath("$.currency").value("ARS")))
                .andExpect(described("JSON $.totalValue isNumber", jsonPath("$.totalValue").isNumber()))
                .andExpect(described("JSON $.totalValue value 1200.00", jsonPath("$.totalValue").value(1200.00)))
                .andExpect(described("JSON $.availableCash isNumber", jsonPath("$.availableCash").isNumber()))
                .andExpect(described("JSON $.availableCash value 400.00", jsonPath("$.availableCash").value(400.00)))
                .andExpect(described("JSON $.positions[0].instrumentId value 2", jsonPath("$.positions[0].instrumentId").value(2)))
                .andExpect(described("JSON $.positions[0].ticker value \"AAA\"", jsonPath("$.positions[0].ticker").value("AAA")))
                .andExpect(described("JSON $.positions[0].name value \"Activo A\"", jsonPath("$.positions[0].name").value("Activo A")))
                .andExpect(described("JSON $.positions[0].quantity isNumber", jsonPath("$.positions[0].quantity").isNumber()))
                .andExpect(described("JSON $.positions[0].quantity value 40", jsonPath("$.positions[0].quantity").value(40)))
                .andExpect(described("JSON $.positions[0].marketPrice isNumber", jsonPath("$.positions[0].marketPrice").isNumber()))
                .andExpect(described("JSON $.positions[0].marketValue isNumber", jsonPath("$.positions[0].marketValue").isNumber()))
                .andExpect(described("JSON $.positions[0].totalReturnPercent isNumber", jsonPath("$.positions[0].totalReturnPercent").isNumber()))
                .andExpect(described("JSON $.positions[0].dailyReturnPercent isNumber", jsonPath("$.positions[0].dailyReturnPercent").isNumber()))
                .andExpect(described("JSON $.positions[0].marketPrice value 80.00", jsonPath("$.positions[0].marketPrice").value(80.00)))
                .andExpect(described("JSON $.positions[0].marketValue value 800.00", jsonPath("$.positions[0].marketValue").value(800.00)))
                .andExpect(described("JSON $.positions[0].totalReturnPercent value 14.2857", jsonPath("$.positions[0].totalReturnPercent").value(14.2857)))
                .andExpect(described("JSON $.positions[0].dailyReturnPercent value 6.6667", jsonPath("$.positions[0].dailyReturnPercent").value(6.6667)));
        response.andExpect(content().string(org.hamcrest.Matchers.containsString("\"quantity\":40")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"quantity\":4E+1"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"totalValue\":1200.00,")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"availableCash\":400.00,")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"marketPrice\":80.00,")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"marketValue\":800.00,")));
    }

    @Test
    @DisplayName("Returns an empty array for an empty portfolio")
    void givenEmptyPortfolio_whenGettingPortfolio_thenPositionsArrayIsEmpty() throws Exception {
        // Given
        when(getPortfolio.execute(1)).thenReturn(
                PortfolioModel.builder().userId(1).currency("ARS").totalValue(BigDecimal.ZERO)
                        .availableCash(BigDecimal.ZERO).positions(List.of()).build());

        // When
        var response = mvc.perform(get("/api/v1/users/1/portfolio"));

        // Then
        response
                .andExpect(described("HTTP status isOk", status().isOk()))
                .andExpect(described("JSON $.totalValue value 0.00", jsonPath("$.totalValue").value(0.00)))
                .andExpect(described("JSON $.positions isEmpty", jsonPath("$.positions").isEmpty()));
    }

    @Test
    @DisplayName("Rounds numeric output without changing nulls")
    void givenFractionalValuesAndNullPrice_whenSerializing_thenRoundingAndNullsArePreserved() throws Exception {
        // Given
        PortfolioPositionModel position = PortfolioPositionModel.builder().instrumentId(2).ticker("AAA")
                .name("Activo").quantity(new BigDecimal("1E+3")).marketPrice(new BigDecimal("1.234567895"))
                .marketValue(new BigDecimal("1E-9")).totalReturnPercent(new BigDecimal("-1.23455"))
                .dailyReturnPercent(new BigDecimal("0.00005")).build();
        when(getPortfolio.execute(1)).thenReturn(PortfolioModel.builder().userId(1).currency("ARS")
                .totalValue(new BigDecimal("1E+3")).availableCash(new BigDecimal("1.005"))
                .positions(List.of(position)).build());
        when(placeOrder.execute(eq(1), any())).thenReturn(PlaceOrderResultModel.builder()
                .order(OrderModel.builder().id(1).userId(1).instrumentId(2).side(SideEnum.BUY)
                        .size(new BigDecimal("0.0000")).type(OrderTypeEnum.LIMIT).status(OrderStatusEnum.NEW)
                        .datetime(NOW).build())
                .build());

        // When
        var response = mvc.perform(get("/api/v1/users/1/portfolio"));

        // Then
        response
                .andExpect(described("HTTP status isOk", status().isOk()))
                .andExpect(described("JSON $.totalValue value 1000.00", jsonPath("$.totalValue").value(1000.00)))
                .andExpect(described("JSON $.availableCash value 1.01", jsonPath("$.availableCash").value(1.01)))
                .andExpect(described("JSON $.positions[0].quantity value 1000", jsonPath("$.positions[0].quantity").value(1000)))
                .andExpect(described("JSON $.positions[0].marketPrice value 1.23", jsonPath("$.positions[0].marketPrice").value(1.23)))
                .andExpect(described("JSON $.positions[0].marketValue value 0.00", jsonPath("$.positions[0].marketValue").value(0.00)))
                .andExpect(described("JSON $.positions[0].totalReturnPercent value -1.2346", jsonPath("$.positions[0].totalReturnPercent").value(-1.2346)))
                .andExpect(described("JSON $.positions[0].dailyReturnPercent value 0.0001", jsonPath("$.positions[0].dailyReturnPercent").value(0.0001)));
        response.andExpect(content().string(org.hamcrest.Matchers.containsString("\"availableCash\":1.01,")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"marketPrice\":1.23,")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"marketValue\":0.00,")));
        mvc.perform(postOrder(VALID_BODY))
                .andExpect(described("HTTP status isCreated", status().isCreated()))
                .andExpect(described("JSON $.quantity value 0", jsonPath("$.quantity").value(0)))
                .andExpect(described("JSON $.price value org.hamcrest.Matchers.nullValue()", jsonPath("$.price").value(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    @DisplayName("Applies search defaults and returns matching items")
    void givenMatchingInstrument_whenSearchingWithoutPagination_thenDefaultsAndItemsAreReturned() throws Exception {
        // Given
        when(searchInstruments.execute(SearchInstrumentsQuery.builder().text("aa").limit(20).offset(0).build()))
                .thenReturn(List.of(InstrumentModel.builder().id(2).ticker("AAA").name("Activo A")
                        .type("ACCIONES").build()));

        // When
        var response = mvc.perform(get("/api/v1/instruments").param("q", "aa"));

        // Then
        response
                .andExpect(described("HTTP status isOk", status().isOk()))
                .andExpect(described("JSON $.items[0].id value 2", jsonPath("$.items[0].id").value(2)))
                .andExpect(described("JSON $.items[0].ticker value \"AAA\"", jsonPath("$.items[0].ticker").value("AAA")))
                .andExpect(described("JSON $.items[0].name value \"Activo A\"", jsonPath("$.items[0].name").value("Activo A")))
                .andExpect(described("JSON $.items[0].type value \"ACCIONES\"", jsonPath("$.items[0].type").value("ACCIONES")))
                .andExpect(described("JSON $.limit value 20", jsonPath("$.limit").value(20)))
                .andExpect(described("JSON $.offset value 0", jsonPath("$.offset").value(0)));
    }

    @Test
    @DisplayName("Returns an empty search result when nothing matches")
    void givenNoMatchingInstruments_whenSearching_thenItemsAreEmpty() throws Exception {
        // Given
        when(searchInstruments.execute(SearchInstrumentsQuery.builder().text("zz").limit(5).offset(10).build())).thenReturn(List.of());

        // When
        var response = mvc.perform(get("/api/v1/instruments").param("q", "zz").param("limit", "5").param("offset", "10"));

        // Then
        response
                .andExpect(described("HTTP status isOk", status().isOk()))
                .andExpect(described("JSON $.items isEmpty", jsonPath("$.items").isEmpty()))
                .andExpect(described("JSON $.limit value 5", jsonPath("$.limit").value(5)))
                .andExpect(described("JSON $.offset value 10", jsonPath("$.offset").value(10)));
    }

    @Test
    @DisplayName("Rejects invalid search parameters")
    void givenInvalidSearchParameters_whenSearching_thenRequestsAreRejected() throws Exception {
        // Given

        // When
        var response = mvc.perform(get("/api/v1/instruments"));

        // Then
        response.andExpect(described("HTTP status isBadRequest", status().isBadRequest()))
                .andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
        mvc.perform(get("/api/v1/instruments").param("q", "a").param("limit", "x"))
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest())).andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
        mvc.perform(get("/api/v1/instruments").param("q", "a").param("offset", "1.5"))
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest())).andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
        mvc.perform(get("/api/v1/instruments").param("q", " "))
                .andExpect(described("HTTP status isBadRequest", status().isBadRequest())).andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));

        verify(searchInstruments, never().description("Invalid pagination does not reach the search port"))
                .execute(any());
    }

    @Test
    @DisplayName("Reports unsupported methods and unknown routes as client errors")
    void givenUnsupportedMethodAndUnknownRoute_whenCallingApi_thenClientErrorsAreReturned() throws Exception {
        // Given
        String unknownRoute = "/api/v1/nothing";

        // When
        var response = mvc.perform(get(ORDERS));

        // Then
        response.andExpect(described("HTTP status isMethodNotAllowed", status().isMethodNotAllowed()))
                .andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
        mvc.perform(get(unknownRoute)).andExpect(described("HTTP status isNotFound", status().isNotFound()))
                .andExpect(described("JSON $.code value \"INVALID_REQUEST\"", jsonPath("$.code").value("INVALID_REQUEST")));
    }

    @RestController
    static class BodyOnlyController {
        @PostMapping("/validate-order")
        void validate(@Valid @RequestBody PlaceOrderRequestDto request) {
        }
    }
}
