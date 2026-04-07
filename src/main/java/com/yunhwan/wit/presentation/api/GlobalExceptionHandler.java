package com.yunhwan.wit.presentation.api;

import com.yunhwan.wit.application.exception.ErrorCode;
import com.yunhwan.wit.application.exception.WitException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WitException.class)
    public ResponseEntity<ApiErrorResponse> handleWitException(WitException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return response(errorCode, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return badRequest(exception.getBindingResult());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException exception) {
        return badRequest(exception.getBindingResult());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiErrorResponse.ValidationError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiErrorResponse.ValidationError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return badRequest(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return response(ErrorCode.INVALID_REQUEST, exception.getParameterName() + "는 필수입니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("Invalid argument: {}", exception.getMessage());
        return response(ErrorCode.INVALID_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        log.error("Unexpected exception occurred", exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiErrorResponse> badRequest(BindingResult bindingResult) {
        List<ApiErrorResponse.ValidationError> errors = bindingResult.getFieldErrors().stream()
                .map(fieldError -> new ApiErrorResponse.ValidationError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        return badRequest(errors);
    }

    private ResponseEntity<ApiErrorResponse> badRequest(List<ApiErrorResponse.ValidationError> errors) {
        return response(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.getMessage(), errors);
    }

    private ResponseEntity<ApiErrorResponse> response(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode));
    }

    private ResponseEntity<ApiErrorResponse> response(ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode, message));
    }

    private ResponseEntity<ApiErrorResponse> response(
            ErrorCode errorCode,
            String message,
            List<ApiErrorResponse.ValidationError> errors
    ) {
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiErrorResponse.of(errorCode, message, errors));
    }
}
