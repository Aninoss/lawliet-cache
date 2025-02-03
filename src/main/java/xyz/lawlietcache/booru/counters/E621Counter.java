package xyz.lawlietcache.booru.counters;

import xyz.lawlietcache.core.WebCache;
import redis.clients.jedis.JedisPool;
import xyz.lawlietcache.util.InternetUtil;

public class E621Counter extends FurryCounter {

    @Override
    public int count(WebCache webCache, JedisPool jedisPool, String tags, boolean withCache) {
        String url = "https://e621.net/posts?page=1&limit=26&tags=" + InternetUtil.escapeForURL(tags + " status:active");
        return countFurry(webCache, url, withCache);
    }

}
