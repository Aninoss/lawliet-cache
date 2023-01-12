package booru.counters;

import core.WebCache;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import util.InternetUtil;

public class DanbooruCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(DanbooruCounter.class);

    @Override
    public int count(WebCache webCache, JedisPool jedisPool, String tags, boolean withCache) {
        String url = "https://danbooru.donmai.us/counts/posts.json?tags=" + InternetUtil.escapeForURL(tags + " status:active");
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

        JSONObject json;
        try {
            json = new JSONObject(data).getJSONObject("counts");
        } catch (JSONException | NullPointerException e) {
            LOGGER.error("Danbooru invalid counter response: {}", data);
            return -1;
        }

        if (json.has("posts") && !json.isNull("posts")) {
            return json.getInt("posts");
        } else {
            return -1;
        }
    }

}
