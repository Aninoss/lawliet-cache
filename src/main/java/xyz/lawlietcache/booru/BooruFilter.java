package xyz.lawlietcache.booru;

import java.util.List;
import java.util.Random;
import redis.clients.jedis.JedisPool;

public class BooruFilter {

    private final JedisPool jedisPool;

    public BooruFilter(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public synchronized BooruImageMeta filter(long guildId, String domain, String searchKey,
                                              List<BooruImageMeta> pornImages, List<String> usedResult,
                                              int maxSize
    ) {
        if (pornImages.size() == 0) {
            return null;
        } else if (pornImages.size() == 1) {
            return pornImages.get(0);
        }

        /* delete global duplicate images */
        BooruImageCacheSearchKey booruImageCacheSearchKey = new BooruImageCacheSearchKey(jedisPool, guildId, domain, searchKey);
        booruImageCacheSearchKey.trim(maxSize);
        pornImages = booruImageCacheSearchKey.filter(pornImages);

        /* delete duplicate images for this command usage */
        pornImages.removeIf(pornImageMeta -> usedResult.contains(pornImageMeta.getImageUrl()));

        long totalWeight = pornImages.stream().mapToLong(BooruImageMeta::getWeight).sum();
        long pos = (long) (new Random().nextDouble() * totalWeight);
        for (BooruImageMeta pornImageMeta : pornImages) {
            if ((pos -= pornImageMeta.getWeight()) < 0) {
                booruImageCacheSearchKey.add(pornImageMeta.getImageUrl());
                usedResult.add(pornImageMeta.getImageUrl());
                return pornImageMeta;
            }
        }

        return null;
    }

}