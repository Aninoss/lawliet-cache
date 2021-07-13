package core;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.NonNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import util.SerializeUtil;

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

    public HttpResponse get(String url) throws IOException {
        Object lock;
        try {
            lock = lockCache.get(url);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        HttpResponse httpResponse;
        synchronized (lock) {
            try (Jedis jedis = jedisPool.getResource()) {
                byte[] key = ("webresponse:" + url).getBytes();
                byte[] data = jedis.get(key);
                if (data == null) {
                    Request request = new Request.Builder()
                            .url(url)
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        httpResponse = new HttpResponse()
                                .setCode(response.code())
                                .setBody(response.body().string());
                        jedis.set(key, SerializeUtil.serialize(httpResponse));
                        jedis.expire(key, Duration.ofMinutes(5).toSeconds());
                    }
                } else {
                    httpResponse = (HttpResponse) SerializeUtil.unserialize(data);
                }
            }
        }

        return httpResponse;
    }

}
