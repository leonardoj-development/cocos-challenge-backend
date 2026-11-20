package com.cocos.challenge.infrastructure.rest.exceptions;

import com.cocos.challenge.domain.exceptions.*;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.servlet.ServletException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Collection;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String INVALID_REQUEST = "INVALID_REQUEST";

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return respond(HttpStatus.BAD_REQUEST, INVALID_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "INSTRUMENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrder(InvalidOrderException ex) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ORDER", ex.getMessage());
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotCancellable(OrderNotCancellableException ex) {
        return respond(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", ex.getMessage());
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataUnavailable(MarketDataUnavailableException ex) {
        return respond(HttpStatus.CONFLICT, "MARKET_DATA_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(InconsistentDataException.class)
    public ResponseEntity<ErrorResponse> handleInconsistentData(InconsistentDataException ex) {
        log.error("Inconsistent data", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INCONSISTENT_DATA", "Inconsistent data");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        String message = "Malformed or invalid JSON request body";
        if (ex.getCause() instanceof UnrecognizedPropertyException) {
            message = "Request body contains an unknown field";
        } else if (ex.getCause() instanceof MismatchedInputException mapping && mapping.getPath().size() == 1) {
            JsonMappingException.Reference reference = mapping.getPath().getFirst();
            String field = reference.getFieldName();
            Class<?> type = mapping.getTargetType();
            if (field != null && field.matches("[a-zA-Z][a-zA-Z0-9_]*") && type != null) {
                if (type == Integer.class || type == int.class) {
                    message = field + " must be a 32-bit integer JSON number (without quotes)";
                } else if (Number.class.isAssignableFrom(type)) {
                    message = field + " must be a JSON number (without quotes)";
                } else if (type == String.class) {
                    message = field + " must be a JSON string";
                } else if (type.isEnum()) {
                    message = field + " has an invalid value";
                }
            }
        }
        return respond(HttpStatus.BAD_REQUEST, INVALID_REQUEST, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        return invalidBody(ex.getBindingResult().getAllErrors());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        if (ex.isForReturnValue()) {
            return handleUnexpected(ex);
        }
        var results = ex.getParameterValidationResults();
        if (results.stream().anyMatch(result ->
                !result.getMethodParameter().hasParameterAnnotation(RequestBody.class))) {
            return respond(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "Invalid request");
        }
        return invalidBody(results.stream()
                .map(ParameterValidationResult::getResolvableErrors)
                .flatMap(Collection::stream)
                .toList());
    }

    private ResponseEntity<ErrorResponse> invalidBody(Collection<? extends MessageSourceResolvable> errors) {
        String message = errors.stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ORDER", message);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, INVALID_REQUEST, "Invalid request");
    }

    @ExceptionHandler(ServletException.class)
    public ResponseEntity<ErrorResponse> handleFramework(ServletException ex) {
        if (ex instanceof org.springframework.web.ErrorResponse framework) {
            HttpStatus status = HttpStatus.resolve(framework.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return respond(status, INVALID_REQUEST, "Invalid request");
            }
        }
        return handleUnexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error");
    }
}
