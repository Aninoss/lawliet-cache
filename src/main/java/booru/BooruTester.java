package booru;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import core.Program;
import core.WebCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

public class BooruTester {

    private final static Logger LOGGER = LoggerFactory.getLogger(BooruTester.class);
    private final static String KEY_BOORU_TESTER_LOCK = "booru_tester_lock";
    private final static String KEY_BOORU_RESULTS = "booru_results";

    private final WebCache webCache;
    private final JedisPool jedisPool;

    public BooruTester(WebCache webCache, JedisPool jedisPool) {
        this.webCache = webCache;
        this.jedisPool = jedisPool;

        if (Program.isProductionMode()) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                try (Jedis jedis = jedisPool.getResource()) {
                    SetParams params = new SetParams();
                    params.ex(Duration.ofMinutes(1).toSeconds());
                    params.nx();
                    String res = jedis.set(KEY_BOORU_TESTER_LOCK, "true", params);
                    if ("OK".equals(res)) {
                        try {
                            schedulerTask();
                        } catch (Throwable e) {
                            LOGGER.error("Exception in scheduler task", e);
                        }
                    }
                }
            }, 0, 10, TimeUnit.SECONDS);
        }
    }

    private void schedulerTask() {
        for (BoardType boardType : BoardType.values()) {
            boolean result = testBoard(boardType);
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.hset(KEY_BOORU_RESULTS, boardType.name(), String.valueOf(result));
            }
            LOGGER.info("Board {}: {}", boardType.name(), result);
        }
    }

    private boolean testBoard(BoardType boardType) {
        try {
            return boardType.count(webCache, jedisPool, "", false) > 0;
        } catch (Throwable e) {
            //ignore
            return false;
        }
    }

    public boolean get(BoardType boardType) {
        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.hget(KEY_BOORU_RESULTS, boardType.name());
            if (result != null) {
                return Boolean.parseBoolean(result);
            } else {
                return false;
            }
        }
    }

}
