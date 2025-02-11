package xyz.lawlietcache.core;

import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Component
public class JedisPoolManager {

    private final JedisPool jedisPool = new JedisPool(
            buildPoolConfig(),
            System.getenv("REDIS_HOST"),
            Integer.parseInt(System.getenv("REDIS_PORT"))
    );

    public JedisPool get() {
        return jedisPool;
    }

    private JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(0);
        return poolConfig;
    }

}
