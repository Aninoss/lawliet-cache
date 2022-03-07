package booru;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class BooruImageCacheSearchKey {

    public static final int MAX_CAP = 250;

    private final JedisPool jedisPool;
    private final long guildId;
    private final String domain;
    private final String searchKey;

    public BooruImageCacheSearchKey(JedisPool jedisPool, long guildId, String domain, String searchKey) {
        this.jedisPool = jedisPool;
        this.guildId = guildId;
        this.domain = domain;
        this.searchKey = searchKey;
        renameOldKey();
    }

    public List<BooruImageMeta> filter(List<BooruImageMeta> imageURLs) {
        try (Jedis jedis = jedisPool.getResource()) {
            ArrayList<BooruImageMeta> newImageUrls = new ArrayList<>(imageURLs);
            List<String> usedImageUrls = jedis.lrange(getKey(), 0, -1);
            newImageUrls.removeIf(imageMeta -> usedImageUrls.contains(imageMeta.getImageUrl()));
            return newImageUrls;
        }
    }

    public void trim(int maxSize) {
        try (Jedis jedis = jedisPool.getResource()) {
            int cap = Math.min(MAX_CAP, maxSize);
            jedis.ltrim(getKey(), 0, cap - 1);
        }
    }

    public void add(String imageURL) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.lpush(getKey(), imageURL);
            jedis.expire(getKey(), Duration.ofDays(3).toSeconds());
        }
    }

    private String getKey() {
        return "booruselected:" + guildId + ":" + domain + ":" + searchKey.hashCode();
    }

    private void renameOldKey() { //TODO: remove after 7 days
        String oldKey = "booruselected:" + guildId + ":" + domain + ":" + searchKey;
        try (Jedis jedis = jedisPool.getResource()) {
            if (jedis.exists(oldKey)) {
                jedis.rename(oldKey, getKey());
            }
        } catch (Throwable e) {
            //ignore
        }
    }

}
