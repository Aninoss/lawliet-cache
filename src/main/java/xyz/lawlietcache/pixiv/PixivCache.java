package xyz.lawlietcache.pixiv;

import com.github.hanshsieh.pixivj.model.Illustration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PixivCache {

    private final JedisPool jedisPool;
    private final long guildId;
    private final String word;

    public PixivCache(JedisPool jedisPool, long guildId, String word) {
        this.jedisPool = jedisPool;
        this.guildId = guildId;
        this.word = word.toLowerCase();
    }

    public List<Illustration> filter(List<Illustration> illustrations) {
        try (Jedis jedis = jedisPool.getResource()) {
            List<Illustration> newIllusts = new ArrayList<>();
            HashSet<String> usedIds = new HashSet<>(jedis.lrange(getKey(), 0, -1));
            for (Illustration illustration : illustrations) {
                String id = String.valueOf(illustration.getId());
                if (!usedIds.contains(id)) {
                    newIllusts.add(illustration);
                }
            }
            return newIllusts;
        }
    }

    public void add(String id) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush(getKey(), id);
            jedis.expire(getKey(), Duration.ofDays(7).toSeconds());
        }
    }

    public void removeFirst() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.lpop(getKey());
        }
    }

    public String getKey() {
        return "pixivselected:" + guildId + ":" + word;
    }

}
