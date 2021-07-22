package core;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import util.SerializeUtil;

public class WebCache {

    private final JedisPool jedisPool;
    private final LockManager lockManager;
    private final OkHttpClient client;

    public WebCache(JedisPool jedisPool, LockManager lockManager) {
        this.jedisPool = jedisPool;
        this.lockManager = lockManager;
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(25);
        ConnectionPool connectionPool = new ConnectionPool(5, 10, TimeUnit.SECONDS);
        this.client = new OkHttpClient.Builder()
                .connectionPool(connectionPool)
                .dispatcher(dispatcher)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .cache(null)
                .build();
    }

    public HttpResponse get(String url, int minutesCached) throws IOException {
        String key = "webresponse:" + url;
        Object lock = lockManager.get(key);

        HttpResponse httpResponse;
        synchronized (lock) {
            try (Jedis jedis = jedisPool.getResource()) {
                byte[] keyBytes = key.getBytes();
                byte[] data = jedis.get(keyBytes);
                if (data == null) {
                    Request request = new Request.Builder()
                            .url(url)
                            .addHeader("User-Agent", "Lawliet Discord Bot made by Aninoss#7220")
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        httpResponse = new HttpResponse()
                                .setCode(response.code())
                                .setBody(response.body().string());
                        jedis.set(keyBytes, SerializeUtil.serialize(httpResponse));
                        jedis.expire(keyBytes, Duration.ofMinutes(minutesCached).toSeconds());
                    }
                } else {
                    httpResponse = (HttpResponse) SerializeUtil.unserialize(data);
                }
            }
        }

        return httpResponse;
    }

}
