package core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;

public class RedisLock implements AutoCloseable {

    private final Jedis jedis;
    private final String key;
    private final boolean freeAtCompletion;
    private final Duration timeoutDuration;
    private boolean keySet = false;

    public RedisLock(Jedis jedis, String key) {
        this(jedis, key, true, Duration.ofMinutes(1));
    }

    public RedisLock(Jedis jedis, String key, boolean freeAtCompletion, Duration timeoutDuration) {
        this.jedis = jedis;
        this.key = key;
        this.freeAtCompletion = freeAtCompletion;
        this.timeoutDuration = timeoutDuration;
    }

    public void blockThread() {
        while (true) {
            if (isFree()) {
                return;
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public boolean isFree() {
        SetParams params = new SetParams();
        params.ex(timeoutDuration.toSeconds());
        params.nx();
        String res = jedis.set(key, "true", params);

        if ("OK".equals(res)) {
            keySet = true;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void close() {
        if (freeAtCompletion && keySet) {
            jedis.del(key);
        }
    }

}
