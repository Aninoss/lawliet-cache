package booru;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import core.WebCache;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.JedisPool;

public class BooruTester {

    private final WebCache webCache;
    private final JedisPool jedisPool;
    private final LoadingCache<BoardType, Boolean> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(3))
            .build(new CacheLoader<>() {
                @Override
                public Boolean load(@NotNull BoardType boardType) {
                    try {
                        return boardType.count(webCache, jedisPool, "", false) > 0;
                    } catch (Throwable e) {
                        //ignore
                        return false;
                    }
                }
            });

    public BooruTester(WebCache webCache, JedisPool jedisPool) {
        this.webCache = webCache;
        this.jedisPool = jedisPool;
    }

    public boolean test(BoardType boardType) {
        try {
            return cache.get(boardType);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

}
