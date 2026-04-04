package com.yunhwan.wit.application.google;

import java.util.Objects;

public record GoogleCallbackCommand(
        String code
) {

    public GoogleCallbackCommand {
        Objects.requireNonNull(code, "code must not be null");
    }
}
