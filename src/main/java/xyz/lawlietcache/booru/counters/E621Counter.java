package xyz.lawlietcache.booru.counters;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import xyz.lawlietcache.core.WebCache;
import xyz.lawlietcache.util.InternetUtil;

public class E621Counter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(E621Counter.class);

    @Override
    public int count(WebCache webCache, JedisPool jedisPool, String tags, boolean withCache) {
        String url = "https://e621.net/posts/count.json?tags=" + InternetUtil.escapeForURL(tags);
        String data;
        try {
            if (withCache) {
                data = webCache.get(url, 1440).getBody();
            } else {
                data = webCache.getWithoutCache(url).getBody();
            }
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", url.split("/")[2], e);
            return -1;
        }

        if (data == null) {
            return -1;
        }

        try {
            return new JSONObject(data).getInt("count");
        } catch (JSONException | NullPointerException e) {
            LOGGER.error("e621 invalid counter response: {}", url);
            return -1;
        }
    }

}
