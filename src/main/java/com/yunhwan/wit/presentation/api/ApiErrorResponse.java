package com.yunhwan.wit.presentation.api;

import com.yunhwan.wit.application.exception.ErrorCode;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        List<ValidationError> errors
) {

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return new ApiErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message) {
        return new ApiErrorResponse(errorCode.getCode(), message, List.of());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, List<ValidationError> errors) {
        return new ApiErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors);
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message, List<ValidationError> errors) {
        return new ApiErrorResponse(errorCode.getCode(), message, errors);
    }

    public record ValidationError(
            String field,
            String message
    ) {
    }
}
