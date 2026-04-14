package com.yunhwan.wit.presentation.api;

import com.yunhwan.wit.application.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "공통 오류 응답")
public record ApiErrorResponse(
        @Schema(description = "오류 코드", example = "COMMON_400")
        String code,
        @Schema(description = "오류 메시지", example = "잘못된 요청입니다.")
        String message,
        @Schema(description = "필드 단위 검증 오류 목록")
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

    @Schema(description = "필드 검증 오류")
    public record ValidationError(
            @Schema(description = "오류 필드", example = "code")
            String field,
            @Schema(description = "오류 메시지", example = "must not be blank")
            String message
    ) {
    }
}
