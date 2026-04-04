package com.yunhwan.wit.application.google;

import java.util.Optional;

public interface GoogleIntegrationRepository {

    Optional<GoogleIntegration> findByUserId(String userId);

    void save(GoogleIntegration googleIntegration);
}
