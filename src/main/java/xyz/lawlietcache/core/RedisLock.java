package xyz.lawlietcache.core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.UUID;

public class RedisLock implements AutoCloseable {

    private static final String HOSTNAME = System.getenv("HOSTNAME");

    private final Jedis jedis;
    private final String lockKey;
    private final String lockValue;

    public RedisLock(Jedis jedis, String key) throws InterruptedException {
        this.jedis = jedis;
        this.lockKey = "lock:" + HOSTNAME + ":" + key;
        this.lockValue = UUID.randomUUID().toString();

        while(true) {
            String result = jedis.set(lockKey, lockValue, SetParams.setParams().nx().ex(14L));
            if("OK".equals(result)) {
                break;
            }
            Thread.sleep(100);
        }
    }

    @Override
    public void close() {
       String script = "if redis.call('get', KEYS[1]) == ARGV[1] then "
                + "return redis.call('del', KEYS[1]) "
                + "else return 0 end";

        jedis.eval(script, 1, lockKey, lockValue);
    }

}
