package com.yunhwan.wit.infrastructure.google;

import com.yunhwan.wit.application.google.GoogleIntegration;
import com.yunhwan.wit.application.google.GoogleIntegrationRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryGoogleIntegrationRepository implements GoogleIntegrationRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryGoogleIntegrationRepository.class);

    private final Map<String, GoogleIntegration> store = new ConcurrentHashMap<>();

    @Override
    public Optional<GoogleIntegration> findByUserId(String userId) {
        log.info(
                "[GoogleIntegrationDebug] repository find before. userId={}, repository={}, storeSize={}",
                userId,
                System.identityHashCode(this),
                store.size()
        );
        Optional<GoogleIntegration> googleIntegration = Optional.ofNullable(store.get(userId));
        log.info(
                "[GoogleIntegrationDebug] repository find after. userId={}, hit={}, repository={}, storeSize={}",
                userId,
                googleIntegration.isPresent(),
                System.identityHashCode(this),
                store.size()
        );
        return googleIntegration;
    }

    @Override
    public void save(GoogleIntegration googleIntegration) {
        log.info(
                "[GoogleIntegrationDebug] repository save before. userId={}, repository={}, storeSize={}",
                googleIntegration.userId(),
                System.identityHashCode(this),
                store.size()
        );
        store.put(googleIntegration.userId(), googleIntegration);
        log.info(
                "[GoogleIntegrationDebug] repository save after. userId={}, repository={}, storeSize={}",
                googleIntegration.userId(),
                System.identityHashCode(this),
                store.size()
        );
    }
}
