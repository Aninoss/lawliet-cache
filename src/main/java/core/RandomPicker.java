package core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RandomPicker {

    private final JedisPool jedisPool;
    private final LockManager lockManager;
    private final Random random = new Random();

    public RandomPicker(JedisPool jedisPool, LockManager lockManager) {
        this.jedisPool = jedisPool;
        this.lockManager = lockManager;
    }

    public synchronized int pick(String tag, long guildId, int size) {
        if (size <= 1) {
            return 0;
        }

        String key = "picks:" + tag + ":" + guildId;
        Object lock = lockManager.get(key);

        synchronized (lock) {
            try (Jedis jedis = jedisPool.getResource()) {
                ArrayList<Integer> options = IntStream.range(0, size)
                        .boxed()
                        .collect(Collectors.toCollection(ArrayList::new));

                jedis.ltrim(key, 0, size - 2);
                jedis.lrange(key, 0, -1).stream()
                        .map(Integer::parseInt)
                        .forEach(options::remove);

                int index = random.nextInt(options.size());
                int n = options.get(index);
                jedis.lpush(key, String.valueOf(n));
                jedis.expire(key, Duration.ofDays(7).toSeconds());

                return n;
            }
        }
    }

}
