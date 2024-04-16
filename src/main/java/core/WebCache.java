package core;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import util.SerializeUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebCache {

    private final static Logger LOGGER = LoggerFactory.getLogger(WebCache.class);
    public static final String USER_AGENT = "Lawliet Discord Bot by @aninoss";
    public static final int MAX_ERRORS = 15;
    public static final String METHOD_GET = "GET";
    public static final String PROXY_COUNTER_KEY = "proxy_counter:";

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
                .callTimeout(10, TimeUnit.SECONDS)
                .cache(null)
                .build();
    }

    public OkHttpClient getClient() {
        return client;
    }

    public HttpResponse get(String url, int minutesCached, HttpHeader... headers) {
        return request(METHOD_GET, url, null, null, minutesCached, new AtomicBoolean(), headers);
    }

    public HttpResponse get(String url, int minutesCached, AtomicBoolean fromCache, HttpHeader... headers) {
        return request(METHOD_GET, url, null, null, minutesCached, fromCache, headers);
    }

    public HttpResponse request(String method, String url, String body, String contentType, int minutesCached, HttpHeader... headers) {
        return request(method, url, body, contentType, minutesCached, new AtomicBoolean(), headers);
    }

    public HttpResponse request(String method, String url, String body, String contentType, int minutesCached, AtomicBoolean fromCache, HttpHeader... headers) {
        String key;
        if (method.equals(METHOD_GET)) {
            key = "webresponse:" + url.hashCode();
        } else {
            key = "webresponse:" + method + ":" + url.hashCode() + ":" + body.hashCode() + ":" + contentType.hashCode();
        }

        Object lock = lockManager.get(key);
        synchronized (lock) {
            try (Jedis jedis = jedisPool.getResource()) {
                byte[] keyBytes = key.getBytes();
                byte[] payloadBytes = jedis.get(keyBytes);

                if (payloadBytes != null) {
                    HttpResponse httpResponse = payloadBytes.length > 0
                            ? (HttpResponse) SerializeUtil.unserialize(payloadBytes)
                            : readHttpResponseFromFile(key);
                    if (httpResponse != null && httpResponse.getBody() != null && Program.isProductionMode()) {
                        fromCache.set(true);
                        return httpResponse;
                    }
                }

                HttpResponse httpResponse = requestWithoutCache(jedis, method, url, body, contentType, headers);
                if (httpResponse.getBody() != null && Program.isProductionMode()) {
                    writeHttpResponseToFile(key, httpResponse);
                    SetParams setParams = new SetParams();
                    setParams.ex(httpResponse.getCode() / 100 != 5 ? Duration.ofMinutes(minutesCached).toSeconds() : 10);
                    jedis.set(keyBytes, new byte[0], setParams);
                }

                fromCache.set(false);
                return httpResponse;
            }
        }
    }

    public HttpResponse getWithoutCache(String url, HttpHeader... headers) {
        try (Jedis jedis = jedisPool.getResource()) {
            return requestWithoutCache(jedis, METHOD_GET, url, null, null, headers);
        }
    }

    private HttpResponse requestWithoutCache(Jedis jedis, String method, String url, String body, String contentType, HttpHeader... headers) {
        String domain = url.split("/")[2];
        if (domain.equals("danbooru.donmai.us")) {
            url += String.format(
                    "&login=%s&api_key=%s",
                    System.getenv("DANBOORU_LOGIN"),
                    System.getenv("DANBOORU_API_TOKEN")
            );
        }
        url = overrideProxyDomains(url, jedis);

        if (!Program.isProductionMode()) {
            LOGGER.info("requesting website: {}", url);
        }

        String domainBlockKey = "domain_block:" + domain;
        String domainOverloadKey = "domain_overload:" + domain;
        String domainBlockValue = jedis.get(domainBlockKey);
        int domainBlockCounter = Optional.ofNullable(domainBlockValue).map(Integer::parseInt).orElse(0);
        if (domainBlockCounter < MAX_ERRORS && checkHostOverload(jedis, domainOverloadKey)) {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT);

            for (HttpHeader header : headers) {
                requestBuilder = requestBuilder.header(header.getName(), header.getValue());
            }

            if (!method.equals(METHOD_GET)) {
                RequestBody requestBody = RequestBody.create(body, MediaType.get(contentType));
                requestBuilder.method(method, requestBody);
            }

            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                if (response.code() / 100 != 2) {
                    LOGGER.warn("Cache: error response {} for url {}", response.code(), url);
                }
                if (response.code() == 503) {
                    jedis.set(domainBlockKey, String.valueOf(MAX_ERRORS));
                } else if (response.code() / 100 == 5) {
                    throw new IOException("Server error");
                } else {
                    long errors = jedis.decr(domainBlockKey);
                    if (errors < 0) {
                        jedis.set(domainBlockKey, "0");
                    }
                }
                return new HttpResponse()
                        .setCode(response.code())
                        .setBody(response.body().string());
            } catch (Throwable e) {
                long errors = jedis.incr(domainBlockKey);
                LOGGER.error("Web cache error ({}; {} errors)", domain, errors);
                if (errors >= MAX_ERRORS) {
                    jedis.set(domainOverloadKey, String.valueOf(System.currentTimeMillis()));
                }
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

    private boolean checkHostOverload(Jedis jedis, String domainOverloadKey) {
        String overloadTimeString = jedis.get(domainOverloadKey);
        long overloadTime = Optional.ofNullable(overloadTimeString).map(Long::parseLong).orElse(0L);
        double threshold = (double) (System.currentTimeMillis() - overloadTime - Duration.ofMinutes(1).toMillis()) / Duration.ofMinutes(9).toMillis();
        return random.nextDouble() < threshold;
    }

    private String overrideProxyDomains(String url, Jedis jedis) {
        String domain = url.split("/")[2].replace("www.", "");
        String counterKey = PROXY_COUNTER_KEY + domain;

        long counter = jedis.incr(counterKey);
        if (counter > 0 && (counter % 2520 == 0)) {
            jedis.decrBy(counterKey, counter);
        }

        for (ProxyTarget proxyTarget : ProxyTarget.values()) {
            if (domain.equals(proxyTarget.getDomain())) {
                String[] proxyDomains = System.getenv("MS_PROXY_HOSTS_" + proxyTarget.name()).split(",");
                String selectedProxyDomain = proxyDomains[(int) (counter % proxyDomains.length)];

                if (!selectedProxyDomain.equals("null")) {
                    String headersEnv = System.getenv("MS_PROXY_HEADERS_" + proxyTarget.name());
                    String header = headersEnv != null ? headersEnv.split(",")[(int) (counter % proxyDomains.length)] : null;

                    if (header != null) {
                        return selectedProxyDomain + "/proxy/" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "/" + URLEncoder.encode(System.getenv("MS_PROXY_AUTH"), StandardCharsets.UTF_8) + "/" + URLEncoder.encode(header, StandardCharsets.UTF_8);
                    } else {
                        return selectedProxyDomain + "/proxy/" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "/" + URLEncoder.encode(System.getenv("MS_PROXY_AUTH"), StandardCharsets.UTF_8);
                    }
                }
                break;
            }
        }
        return url;
    }

    private void writeHttpResponseToFile(String key, HttpResponse httpResponse) {
        String filePath = System.getenv("WEBCACHE_ROOT_PATH") + "/" + key.replace(":", "_");
        File file = new File(filePath);

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] payloadBytes = SerializeUtil.serialize(httpResponse);
            if (payloadBytes != null) {
                outputStream.write(payloadBytes);
            } else {
                LOGGER.error("Payload bytes for file {} are null", filePath);
            }
        } catch (IOException e) {
            LOGGER.error("Could not write cache file {}", filePath, e);
        }
    }

    private HttpResponse readHttpResponseFromFile(String key) {
        String filePath = System.getenv("WEBCACHE_ROOT_PATH") + "/" + key.replace(":", "_");
        File file = new File(filePath);

        if (!file.exists()) {
            LOGGER.error("Could not find file {}", filePath);
            return null;
        }

        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] payloadBytes = inputStream.readAllBytes();
            return (HttpResponse) SerializeUtil.unserialize(payloadBytes);
        } catch (IOException e) {
            LOGGER.error("Could not read cache file {}", filePath, e);
            return null;
        }
    }

}
