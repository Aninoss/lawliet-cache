package booru.counters;

import core.WebCache;
import redis.clients.jedis.JedisPool;
import util.InternetUtil;

public class Rule34Counter extends SearchCounter {

    @Override
    public int count(WebCache webCache, JedisPool jedisPool, String tags, boolean withCache) {
        String url = "https://api.rule34.xxx/index.php?page=dapi&s=post&q=index&json=0&limit=1&tags=" + InternetUtil.escapeForURL(tags);
        return countSearch(webCache, url, withCache);
    }

}
