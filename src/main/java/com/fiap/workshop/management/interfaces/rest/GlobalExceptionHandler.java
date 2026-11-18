package com.fiap.workshop.management.interfaces.rest;

import com.fiap.workshop.management.domain.exception.DuplicateResourceException;
import com.fiap.workshop.management.domain.exception.InvalidWebhookTokenException;
import com.fiap.workshop.management.domain.exception.ResourceInUseException;
import com.fiap.workshop.management.domain.exception.InvalidDocumentException;
import com.fiap.workshop.management.domain.exception.InvalidLicensePlateException;
import com.fiap.workshop.management.domain.exception.InvalidStatusTransitionException;
import com.fiap.workshop.management.domain.exception.ResourceNotFoundException;
import com.fiap.workshop.management.domain.exception.SupplyCatalogUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    record ErrorResponse(int status, String error, String message, LocalDateTime timestamp) {}

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(BadCredentialsException ex) {
        return new ErrorResponse(401, "Unauthorized", "Invalid credentials", LocalDateTime.now());
    }

    @ExceptionHandler(InvalidWebhookTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidWebhookToken(InvalidWebhookTokenException ex) {
        return new ErrorResponse(401, "Unauthorized", ex.getMessage(), LocalDateTime.now());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMalformedRequest(HttpMessageNotReadableException ex) {
        return new ErrorResponse(400, "Bad Request", "Malformed request body", LocalDateTime.now());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUnknownPath(NoResourceFoundException ex) {
        return new ErrorResponse(404, "Not Found", "Resource not found", LocalDateTime.now());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponse(404, "Not Found", ex.getMessage(), LocalDateTime.now());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(DuplicateResourceException ex) {
        return new ErrorResponse(409, "Conflict", ex.getMessage(), LocalDateTime.now());
    }

    @ExceptionHandler(ResourceInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleResourceInUse(ResourceInUseException ex) {
        return new ErrorResponse(409, "Conflict", ex.getMessage(), LocalDateTime.now());
    }

    @ExceptionHandler({InvalidDocumentException.class, InvalidLicensePlateException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(RuntimeException ex) {
        return new ErrorResponse(400, "Bad Request", ex.getMessage(), LocalDateTime.now());
    }

    @ExceptionHandler(SupplyCatalogUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleSupplyCatalogUnavailable(SupplyCatalogUnavailableException ex) {
        log.warn("Inventory lookup failed: {}", ex.getMessage(), ex);
        return new ErrorResponse(503, "Service Unavailable", ex.getMessage(), LocalDateTime.now());
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleUnprocessable(InvalidStatusTransitionException ex) {
        return new ErrorResponse(422, "Unprocessable Entity", ex.getMessage(), LocalDateTime.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, fe ->
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid value",
                        (a, b) -> a));
        return Map.of(
                "status", 400,
                "error", "Validation Failed",
                "fields", fieldErrors,
                "timestamp", LocalDateTime.now().toString()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred", LocalDateTime.now());
    }
}
