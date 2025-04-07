package xyz.lawlietcache.reddit;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;

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

    public void filter(Collection<RedditPost> postList) {
        try (Jedis jedis = jedisPool.getResource()) {
            HashSet<String> usedPostIds = new HashSet<>(jedis.lrange(getKey(), 0, -1));
            postList.removeIf(post -> usedPostIds.contains(post.getId()));
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
