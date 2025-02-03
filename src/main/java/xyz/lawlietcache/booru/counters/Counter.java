package xyz.lawlietcache.booru.counters;

import xyz.lawlietcache.core.WebCache;
import redis.clients.jedis.JedisPool;

public interface Counter {

    int count(WebCache webCache, JedisPool jedisPool, String tags, boolean withCache);

}
