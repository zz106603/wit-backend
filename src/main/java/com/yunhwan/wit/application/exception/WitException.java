package com.yunhwan.wit.application.exception;

public class WitException extends RuntimeException {

    private final ErrorCode errorCode;

    public WitException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public WitException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
