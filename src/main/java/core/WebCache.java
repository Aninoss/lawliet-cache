package core;

import java.io.InterruptedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import util.SerializeUtil;

public class WebCache {

    private final static Logger LOGGER = LoggerFactory.getLogger(WebCache.class);
    public static final String USER_AGENT = "Lawliet Discord Bot made by Aninoss#7220";

    private final JedisPool jedisPool;
    private final LockManager lockManager;
    private final OkHttpClient client;
    private final Random random = new Random();

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

    public HttpResponse get(String url, int minutesCached) {
        if (url.startsWith("https://danbooru.donmai.us/")) {
            url += String.format("&login=%s&api_key=%s", System.getenv("DANBOORU_LOGIN"), System.getenv("DANBOORU_API_TOKEN"));
            String[] proxyDomains = System.getenv("MS_PROXY_HOSTS").split(",");
            String selectedProxyDomain = proxyDomains[random.nextInt(proxyDomains.length)];
            url = "https://" + selectedProxyDomain + "/proxy/" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "/" + URLEncoder.encode(System.getenv("MS_PROXY_AUTH"), StandardCharsets.UTF_8);
        }

        if (!Program.isProductionMode()) {
            LOGGER.info("caching website: {}", url);
        }

        String key = "webresponse:" + url.hashCode();
        Object lock = lockManager.get(key);

        HttpResponse httpResponse;
        synchronized (lock) {
            try (Jedis jedis = jedisPool.getResource()) {
                byte[] keyBytes = key.getBytes();
                byte[] data = jedis.get(keyBytes);
                if (data == null) {
                    httpResponse = getWithoutCache(jedis, url);
                    jedis.set(keyBytes, SerializeUtil.serialize(httpResponse));
                    jedis.expire(keyBytes, Duration.ofMinutes(httpResponse.getCode() / 100 != 5 ? minutesCached : 1).toSeconds());
                    if (!Program.isProductionMode()) {
                        LOGGER.info("new cache entry for: {}", url);
                    }
                } else {
                    httpResponse = (HttpResponse) SerializeUtil.unserialize(data);
                }
            }
        }

        return httpResponse;
    }

    public HttpResponse getWithoutCache(Jedis jedis, String url) {
        String domain = url.split("/")[2];
        String domainBlockKey = "domain_block:" + domain;
        String domainBlockValue = jedis.get(domainBlockKey);
        int domainBlockCounter = Optional.ofNullable(domainBlockValue).map(Integer::parseInt).orElse(0);
        if (domainBlockCounter < 20) {
            try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(9))) {
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", USER_AGENT)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    jedis.set(domainBlockKey, "0");
                    return new HttpResponse()
                            .setCode(response.code())
                            .setBody(response.body().string());
                }
            } catch (InterruptedIOException e) {
                long errors = jedis.incr(domainBlockKey);
                LOGGER.error("Web cache time out ({}; {} errors)", domain, errors);
                return new HttpResponse()
                        .setCode(500);
            } catch (Throwable e) {
                LOGGER.error("Web cache connection error ({})", domain);
                return new HttpResponse()
                        .setCode(500);
            } finally {
                jedis.expire(domainBlockKey, Duration.ofMinutes(1).toSeconds());
            }
        } else {
            return new HttpResponse()
                    .setCode(500);
        }
    }

    public OkHttpClient getClient() {
        return client;
    }

}
