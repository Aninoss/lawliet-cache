package core;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.checkerframework.checker.nullness.qual.NonNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class WebCache {

    private final OkHttpClient client = new OkHttpClient();
    private final JedisPool jedisPool;
    private final LoadingCache<String, Object> lockCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<>() {
                @Override
                public Object load(@NonNull String key) throws Exception {
                    return new Object();
                }
            });

    public WebCache(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public String get(String url) throws IOException {
        Object lock;
        try {
            lock = lockCache.get(url);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        String content;
        synchronized (lock) {
            try(Jedis jedis = jedisPool.getResource()) {
                String key = "web:" + url;
                content = jedis.get(key);
                if (content == null) {
                    Request request = new Request.Builder()
                            .url(url)
                            .build();

                    content = client.newCall(request).execute().body().string();
                    jedis.set(key, content);
                    jedis.expire(key, Duration.ofMinutes(5).toSeconds());
                }
            }
        }

        return content;
    }

}
