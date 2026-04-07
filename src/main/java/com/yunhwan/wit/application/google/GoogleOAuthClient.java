package com.yunhwan.wit.application.google;

public interface GoogleOAuthClient {

    String buildLoginUrl();

    GoogleOAuthToken exchangeCode(String code, String state);
}
