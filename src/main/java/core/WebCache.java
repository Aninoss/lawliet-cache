package core;

import java.io.InterruptedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLHandshakeException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import util.SerializeUtil;

public class WebCache {

    private final static Logger LOGGER = LoggerFactory.getLogger(WebCache.class);
    public static final String USER_AGENT = "Lawliet Discord Bot made by Aninoss#7220";
    public static final int MAX_ERRORS = 20;
    public static final String METHOD_GET = "GET";

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
        return request(METHOD_GET, url, null, null, minutesCached);
    }

    public HttpResponse request(String method, String url, String body, String contentType, int minutesCached) {
        String key;
        if (method.equals(METHOD_GET)) {
            key = "webresponse:" + url.hashCode();
        } else {
            key = "webresponse:" + method + ":" + url.hashCode() + ":" + body.hashCode() + ":" + contentType;
        }

        Object lock = lockManager.get(key);
        HttpResponse httpResponse;
        synchronized (lock) {
            try (Jedis jedis = jedisPool.getResource()) {
                byte[] keyBytes = key.getBytes();
                byte[] data = jedis.get(keyBytes);
                if (data == null || !Program.isProductionMode()) {
                    httpResponse = requestWithoutCache(jedis, method, url, body, contentType);
                    SetParams setParams = new SetParams();
                    setParams.ex(httpResponse.getCode() / 100 != 5 ? Duration.ofMinutes(minutesCached).toSeconds() : 10);
                    jedis.set(keyBytes, SerializeUtil.serialize(httpResponse), setParams);
                } else {
                    httpResponse = (HttpResponse) SerializeUtil.unserialize(data);
                }
            }
        }

        return httpResponse;
    }

    public HttpResponse getWithoutCache(String url) {
        try (Jedis jedis = jedisPool.getResource()) {
            return requestWithoutCache(jedis, METHOD_GET, url, null, null);
        }
    }

    public HttpResponse requestWithoutCache(String method, String url, String body, String contentType) {
        try (Jedis jedis = jedisPool.getResource()) {
            return requestWithoutCache(jedis, method, url, body, contentType);
        }
    }

    private HttpResponse requestWithoutCache(Jedis jedis, String method, String url, String body, String contentType) {
        String domain = url.split("/")[2];
        if (domain.equals("danbooru.donmai.us")) {
            url += String.format("&login=%s&api_key=%s",
                    System.getenv("DANBOORU_LOGIN"),
                    System.getenv("DANBOORU_API_TOKEN")
            );
        }
        url = overrideProxyDomains(url);

        if (!Program.isProductionMode()) {
            LOGGER.info("requesting website: {}", url);
        }

        String domainBlockKey = "domain_block:" + domain;
        String domainBlockValue = jedis.get(domainBlockKey);
        int domainBlockCounter = Optional.ofNullable(domainBlockValue).map(Integer::parseInt).orElse(0);
        if (domainBlockCounter < MAX_ERRORS) {
            try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(9))) {
                Request.Builder requestBuilder = new Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", USER_AGENT);

                if (!method.equals(METHOD_GET)) {
                    RequestBody requestBody = RequestBody.create(body, MediaType.get(contentType));
                    requestBuilder.method(method, requestBody);
                }

                try (Response response = client.newCall(requestBuilder.build()).execute()) {
                    if (domain.equals("e621.net") && response.code() == 503) {
                        jedis.set(domainBlockKey, String.valueOf(MAX_ERRORS));
                    } else {
                        jedis.set(domainBlockKey, "0");
                    }
                    return new HttpResponse()
                            .setCode(response.code())
                            .setBody(response.body().string());
                }
            } catch (InterruptedIOException | SSLHandshakeException e) {
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

    private String overrideProxyDomains(String url) {
        String domain = url.split("/")[2];
        for (ProxyTarget proxyTarget : ProxyTarget.values()) {
            if (domain.equals(proxyTarget.getDomain())) {
                String[] rawProxyDomains = System.getenv("MS_PROXY_HOSTS").split(",");
                String[] proxyDomains;
                if (proxyTarget.allowWithoutProxy()) {
                    proxyDomains = new String[rawProxyDomains.length + 1];
                    System.arraycopy(rawProxyDomains, 0, proxyDomains, 0, rawProxyDomains.length);
                } else {
                    proxyDomains = rawProxyDomains;
                }
                String selectedProxyDomain = proxyDomains[random.nextInt(proxyDomains.length)];
                if (selectedProxyDomain != null) {
                    return "https://" + selectedProxyDomain + "/proxy/" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "/" + URLEncoder.encode(System.getenv("MS_PROXY_AUTH"), StandardCharsets.UTF_8);
                }
                break;
            }
        }
        return url;
    }

    public OkHttpClient getClient() {
        return client;
    }

}
