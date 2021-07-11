package booru;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.checkerframework.checker.nullness.qual.NonNull;

public class BooruImageCache {

    private final LoadingCache<Integer, BooruImageCacheSearchKey> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .build(
                    new CacheLoader<>() {
                        @Override
                        public BooruImageCacheSearchKey load(@NonNull Integer hash) {
                            return new BooruImageCacheSearchKey();
                        }
                    }
            );

    public BooruImageCacheSearchKey get(long guildId, @NonNull String domain, @NonNull String searchKey) {
        try {
            int hash = Objects.hash(guildId, domain, searchKey.toLowerCase());
            return cache.get(hash);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}