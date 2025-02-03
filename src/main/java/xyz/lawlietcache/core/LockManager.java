package xyz.lawlietcache.core;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

@Service
public class LockManager {

    private final LoadingCache<String, Object> lockCache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(new CacheLoader<>() {
                @Override
                public Object load(@NonNull String key) {
                    return new Object();
                }
            });

    public Object get(String key) {
        try {
            return lockCache.get(key);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
