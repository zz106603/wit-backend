package com.yunhwan.wit.application.google;

import com.yunhwan.wit.application.exception.ErrorCode;
import com.yunhwan.wit.application.exception.WitException;

public class GoogleReauthenticationRequiredException extends WitException {

    public GoogleReauthenticationRequiredException() {
        super(ErrorCode.GOOGLE_REAUTH_REQUIRED);
    }

    public GoogleReauthenticationRequiredException(String message) {
        super(ErrorCode.GOOGLE_REAUTH_REQUIRED, message);
    }
}
