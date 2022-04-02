package booru.counters;

import core.WebCache;
import redis.clients.jedis.JedisPool;
import util.InternetUtil;

public class KonachanCounter extends SearchCounter {

    @Override
    public int count(WebCache webCache, JedisPool jedisPool, String tags, boolean withCache) {
        String url = "https://konachan.com/post.xml?limit=1&tags=" + InternetUtil.escapeForURL(tags);
        return countSearch(webCache, url, withCache);
    }

}
