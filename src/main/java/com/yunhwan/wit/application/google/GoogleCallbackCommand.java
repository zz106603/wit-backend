package com.yunhwan.wit.application.google;

import java.util.Objects;

public record GoogleCallbackCommand(
        String code,
        String state
) {

    public GoogleCallbackCommand {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(state, "state must not be null");
    }
}
