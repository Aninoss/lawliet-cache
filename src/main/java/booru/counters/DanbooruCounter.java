package booru.counters;

import java.time.Duration;
import core.WebCache;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import util.InternetUtil;

public class DanbooruCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(DanbooruCounter.class);
    private static final String KEY_DANBOORU_BLOCK = "danbooru_block";

    @Override
    public int count(WebCache webCache, JedisPool jedisPool, String tags) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (jedis.exists(KEY_DANBOORU_BLOCK)) {
                return 0;
            }
        }

        String url = "https://danbooru.donmai.us/counts/posts.json?tags=" + InternetUtil.escapeForURL(tags + " status:active");
        String data;
        try {
            data = webCache.get(url, 15).getBody();
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", url.split("/")[2], e);
            return 0;
        }

        JSONObject json;
        try {
            json = new JSONObject(data).getJSONObject("counts");
        } catch (JSONException | NullPointerException e) {
            LOGGER.error("Danbooru invalid counter response: {}", data);
            try (Jedis jedis = jedisPool.getResource()) {
                SetParams setParams = new SetParams();
                setParams.ex(Duration.ofMinutes(1).toSeconds());
                jedis.set(KEY_DANBOORU_BLOCK, "true", setParams);
            }
            return 0;
        }


        if (json.has("posts") && !json.isNull("posts")) {
            return json.getInt("posts");
        } else {
            return 0;
        }
    }

}
