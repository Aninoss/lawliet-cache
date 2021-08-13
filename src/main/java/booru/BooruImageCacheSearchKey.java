package booru;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    public List<BooruImageMeta> filter(List<BooruImageMeta> imageURLs) {
        ArrayList<BooruImageMeta> newImageUrls = new ArrayList<>(imageURLs);
        List<String> usedImageUrls = jedis.lrange(getKey(), 0, -1);
        newImageUrls.removeIf(imageMeta -> usedImageUrls.contains(imageMeta.getImageUrl()));
        return newImageUrls;
    }

    public void trim(int maxSize) {
        int cap = Math.min(MAX_CAP, maxSize);
        jedis.ltrim(getKey(), 0, cap - 1);
    }

    public void add(String imageURL) {
        jedis.lpush(getKey(), imageURL);
        jedis.expire(getKey(), Duration.ofDays(7).toSeconds());
    }

    private String getKey() {
        return "booruselected:" + guildId + ":" + domain + ":" + searchKey;
    }

}
