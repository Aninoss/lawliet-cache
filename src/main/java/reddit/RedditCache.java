package reddit;

import java.time.Duration;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedditCache {

    private final JedisPool jedisPool;
    private final long guildId;
    private final String subreddit;
    private final String orderBy;

    public RedditCache(JedisPool jedisPool, long guildId, String subreddit, String orderBy) {
        this.jedisPool = jedisPool;
        this.guildId = guildId;
        this.subreddit = subreddit;
        this.orderBy = orderBy;
    }

    public JSONArray filter(JSONArray postArrayJson) {
        try (Jedis jedis = jedisPool.getResource()) {
            JSONArray newPostArrayJson = new JSONArray();
            HashSet<String> usedPostIds = new HashSet<>(jedis.lrange(getKey(), 0, -1));
            for (int i = 0; i < postArrayJson.length(); i++) {
                JSONObject postJson = postArrayJson.getJSONObject(i);
                JSONObject dataJson = postJson.getJSONObject("data");
                String id = dataJson.getString("name");
                if (!usedPostIds.contains(id)) {
                    newPostArrayJson.put(postJson);
                }
            }
            return newPostArrayJson;
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
        return "redditselected:" + guildId + ":" + subreddit + ":" + orderBy;
    }

}
