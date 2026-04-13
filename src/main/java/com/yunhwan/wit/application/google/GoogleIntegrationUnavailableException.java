package com.yunhwan.wit.application.google;

import com.yunhwan.wit.application.exception.ErrorCode;
import com.yunhwan.wit.application.exception.WitException;

public class GoogleIntegrationUnavailableException extends WitException {

    public GoogleIntegrationUnavailableException(String message) {
        super(ErrorCode.GOOGLE_INTEGRATION_UNAVAILABLE, message);
    }

    public GoogleIntegrationUnavailableException(String message, Throwable cause) {
        super(ErrorCode.GOOGLE_INTEGRATION_UNAVAILABLE, message);
        initCause(cause);
    }
}
