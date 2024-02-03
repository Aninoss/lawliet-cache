package core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RandomPicker {

    private final static String KEY_RANDOM_PICKER_LOCK = "random_picker_lock:";

    private final JedisPool jedisPool;
    private final Random random = new Random();

    public RandomPicker(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public synchronized int pick(String tag, long guildId, int size) {
        if (size <= 1) {
            return 0;
        }

        String key = "picks:" + tag + ":" + guildId;
        try (Jedis jedis = jedisPool.getResource();
             RedisLock redisLock = new RedisLock(jedis, KEY_RANDOM_PICKER_LOCK + key)
        ) {
            redisLock.blockThread();

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
