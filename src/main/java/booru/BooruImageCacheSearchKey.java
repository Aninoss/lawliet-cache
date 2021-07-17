package booru;

import java.time.Duration;
import redis.clients.jedis.Jedis;

public class BooruImageCacheSearchKey {

    public static final int MAX_CAP = 250;

    private final Jedis jedis;
    private final long guildId;
    private final String domain;
    private final String searchKey;

    public BooruImageCacheSearchKey(Jedis jedis, long guildId, String domain, String searchKey) {
        this.jedis = jedis;
        this.guildId = guildId;
        this.domain = domain;
        this.searchKey = searchKey;
    }

    public synchronized boolean contains(String imageURL) {
        return jedis.zscore(getKey(), imageURL) != null;
    }

    public synchronized void trim(int maxSize) {
        int cap = Math.min(MAX_CAP, maxSize);
        jedis.zremrangeByRank(getKey(), 0, -1 - cap);
    }

    public synchronized void add(String imageURL) {
        jedis.zadd(getKey(), System.currentTimeMillis(), imageURL);
        jedis.expire(getKey(), Duration.ofMinutes(30).toSeconds());
    }

    private String getKey() {
        return "booruselected:" + guildId + ":" + domain + ":" + searchKey;
    }

}
