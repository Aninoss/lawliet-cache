package pixiv;

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

    public List<CustomIllustration> filter(List<CustomIllustration> illusts) {
        try (Jedis jedis = jedisPool.getResource()) {
            List<CustomIllustration> newIllusts = new ArrayList<>();
            HashSet<String> usedIds = new HashSet<>(jedis.lrange(getKey(), 0, -1));
            for (CustomIllustration illust : illusts) {
                String id = String.valueOf(illust.getId());
                if (!usedIds.contains(id)) {
                    newIllusts.add(illust);
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
