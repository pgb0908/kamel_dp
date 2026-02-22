package com.mycompany.integration.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.apache.camel.spi.IdempotentRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class IdempotentRepositoryConfig {

    private static final Logger log = LoggerFactory.getLogger(IdempotentRepositoryConfig.class);

    @ConfigProperty(name = "app.idempotent.max-size", defaultValue = "10000")
    long maxSize;

    @ConfigProperty(name = "app.idempotent.expire-after-write-seconds", defaultValue = "86400")
    long expireAfterWriteSeconds;

    @Produces
    @Named("orderIdempotentRepository")
    @ApplicationScoped
    public IdempotentRepository orderIdempotentRepository() {
        Cache<String, Boolean> caffeineCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWriteSeconds, TimeUnit.SECONDS)
                .build();

        log.info("CaffeineIdempotentRepository created — maxSize={}, ttl={}s", maxSize, expireAfterWriteSeconds);

        return new CaffeineBackedIdempotentRepository(caffeineCache);
    }

    /**
     * Caffeine 캐시를 백엔드로 사용하는 IdempotentRepository 구현체.
     * TTL 및 maxSize를 설정 가능합니다.
     */
    static class CaffeineBackedIdempotentRepository implements IdempotentRepository {

        private final Cache<String, Boolean> cache;

        CaffeineBackedIdempotentRepository(Cache<String, Boolean> cache) {
            this.cache = cache;
        }

        @Override
        public boolean add(String key) {
            if (cache.getIfPresent(key) != null) {
                return false;
            }
            cache.put(key, Boolean.TRUE);
            return true;
        }

        @Override
        public boolean contains(String key) {
            return cache.getIfPresent(key) != null;
        }

        @Override
        public boolean remove(String key) {
            boolean existed = cache.getIfPresent(key) != null;
            cache.invalidate(key);
            return existed;
        }

        @Override
        public boolean confirm(String key) {
            return true;
        }

        @Override
        public void clear() {
            cache.invalidateAll();
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}
    }
}
