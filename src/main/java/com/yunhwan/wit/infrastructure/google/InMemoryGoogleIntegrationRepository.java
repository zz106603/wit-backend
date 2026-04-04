package com.yunhwan.wit.infrastructure.google;

import com.yunhwan.wit.application.google.GoogleIntegration;
import com.yunhwan.wit.application.google.GoogleIntegrationRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGoogleIntegrationRepository implements GoogleIntegrationRepository {

    private final Map<String, GoogleIntegration> store = new ConcurrentHashMap<>();

    @Override
    public Optional<GoogleIntegration> findByUserId(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public void save(GoogleIntegration googleIntegration) {
        store.put(googleIntegration.userId(), googleIntegration);
    }
}
