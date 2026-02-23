package xyz.lawlietcache.core;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class LockManager {

    private final LoadingCache<String, Object> lockCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
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
