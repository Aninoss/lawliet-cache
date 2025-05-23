package xyz.lawlietcache.booru;

import net.kodehawa.lib.imageboards.ImageBoard;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import xyz.lawlietcache.core.Program;
import xyz.lawlietcache.core.WebCache;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BooruIdThresholdFinder {

    private final static Logger LOGGER = LoggerFactory.getLogger(BooruIdThresholdFinder.class);
    private final static String KEY_BOORU_ID_THRESHOLD_FINDER_LOCK = "booru_id_threshold_finder_lock";
    private final static String KEY_BOORU_ID_RESULTS = "booru_id_thresholds";

    private final OkHttpClient client;
    private final JedisPool jedisPool;
    private final WebCache webCache;

    public BooruIdThresholdFinder(OkHttpClient client, JedisPool jedisPool, WebCache webCache) {
        this.client = client;
        this.jedisPool = jedisPool;
        this.webCache = webCache;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                SetParams params = new SetParams();
                params.ex(Duration.ofMinutes(15).toSeconds());
                params.nx();
                String res = jedis.set(KEY_BOORU_ID_THRESHOLD_FINDER_LOCK, "true", params);
                if (!Program.isProductionMode() || "OK".equals(res)) {
                    schedulerTask();
                }
            } catch (Throwable e) {
                LOGGER.error("Exception in scheduler task", e);
            }
        }, 1, 5, TimeUnit.MINUTES);
    }

    private void schedulerTask() {
        for (BoardType boardType : BoardType.values()) {
            Long id = null;
            Integer pageEstimate = getThresholdIdPageEstimate(boardType);
            if (pageEstimate != null) {
                id = getThresholdId(boardType, pageEstimate);
            }

            if (id != null) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.hset(KEY_BOORU_ID_RESULTS, boardType.name(), String.valueOf(id));
                }
            }
            LOGGER.info("Board ID {}: {}", boardType.name(), id);
        }
    }

    private Integer getThresholdIdPageEstimate(BoardType boardType) {
        ImageBoard<? extends BoardImage> imageBoard = new ImageBoard<>(client, boardType.getBoard(),
                boardType.getResponseFormat(), boardType.getBoardImageClass());

        try {
            for (int page = 0; page < 250; page += 5) {
                List<? extends BoardImage> images;
                if (boardType.getWorkaroundSearcher() != null) {
                    images = boardType.getWorkaroundSearcher().search(page, "", webCache);
                } else {
                    images = imageBoard.search(page, boardType.getMaxLimit(), "")
                            .blocking();
                }
                long maxPostDate = System.currentTimeMillis() - Duration.ofDays(3).toMillis();

                for (BoardImage image : images) {
                    if (image.getCreationMillis() <= maxPostDate) {
                        return page;
                    }
                }
            }
            return null;
        } catch (Throwable e) {
            LOGGER.error("Exception", e);
            return null;
        }
    }

    private Long getThresholdId(BoardType boardType, int pageEstimate) {
        ImageBoard<? extends BoardImage> imageBoard = new ImageBoard<>(client, boardType.getBoard(),
                boardType.getResponseFormat(), boardType.getBoardImageClass());

        try {
            for (int page = Math.max(0, pageEstimate - 4); page <= pageEstimate; page++) {
                List<? extends BoardImage> images;
                if (boardType.getWorkaroundSearcher() != null) {
                    images = boardType.getWorkaroundSearcher().search(page, "", webCache);
                } else {
                    images = imageBoard.search(page, boardType.getMaxLimit(), "")
                            .blocking();
                }
                long maxPostDate = System.currentTimeMillis() - Duration.ofDays(3).toMillis();

                for (BoardImage image : images) {
                    if (image.getCreationMillis() <= maxPostDate) {
                        return image.getId();
                    }
                }
            }
            return null;
        } catch (Throwable e) {
            LOGGER.error("Exception", e);
            return null;
        }
    }

    public Long get(BoardType boardType) {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.hget(KEY_BOORU_ID_RESULTS, boardType.name());
            if (result != null) {
                return Long.parseLong(result);
            } else {
                return null;
            }
        }
    }

}
