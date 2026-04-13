package com.yunhwan.wit.application.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    GOOGLE_REAUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "GOOGLE_401", "Google 재연동이 필요합니다."),
    GOOGLE_INTEGRATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_503", "Google 연동 중 오류가 발생했습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
