package xyz.lawlietcache.reddit;

import org.springframework.stereotype.Service;
import xyz.lawlietcache.core.HttpHeader;
import xyz.lawlietcache.core.HttpResponse;
import xyz.lawlietcache.core.WebCache;
import xyz.lawlietcache.util.InternetUtil;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RedditApisClient {

    public static final String DOMAIN = "api.redditapis.com";
    private static final String BASE_URL = "https://" + DOMAIN;

    private final WebCache webCache;
    private final HttpHeader authHeader;

    public RedditApisClient(WebCache webCache) {
        String apiKey = System.getenv("REDDITAPIS_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing environment variable REDDITAPIS_KEY");
        }

        this.webCache = webCache;
        this.authHeader = new HttpHeader("Authorization", "Bearer " + apiKey.trim());
    }

    public HttpResponse get(String path, Map<String, String> params, int minutesCached, AtomicBoolean fromCache) {
        StringBuilder url = new StringBuilder(BASE_URL).append(path);
        char separator = '?';
        for (Map.Entry<String, String> param : params.entrySet()) {
            url.append(separator)
                    .append(InternetUtil.escapeForURL(param.getKey()))
                    .append('=')
                    .append(InternetUtil.escapeForURL(param.getValue()));
            separator = '&';
        }

        return webCache.get(url.toString(), minutesCached, fromCache, authHeader);
    }

}
