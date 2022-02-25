package reddit;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import core.HttpResponse;
import core.WebCache;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import util.InternetUtil;

public class RedditDownloader {

    private final static Logger LOGGER = LoggerFactory.getLogger(RedditDownloader.class);
    private final static String KEY_REDDIT_BLOCK = "reddit_block";
    private final static int TIMEOUT_MIN = 60;
    private final static int MAX_PAGE = 2;

    private final WebCache webCache;
    private final JedisPool jedisPool;

    public RedditDownloader(WebCache webCache, JedisPool jedisPool) {
        this.webCache = webCache;
        this.jedisPool = jedisPool;
    }

    public RedditPost retrievePost(long guildId, String subreddit, String orderBy, boolean nsfwAllowed) {
        int tries = 5;
        RedditPost redditPost;
        do {
            redditPost = retrievePostRaw(guildId, subreddit, orderBy, null, 0);
        } while (--tries > 0 && !nsfwAllowed && (redditPost != null && redditPost.isNsfw()));

        return redditPost;
    }

    public List<RedditPost> retrievePostsBulk(String subreddit, String orderBy) {
        JSONArray postArrayJson = retrievePostArray(subreddit, orderBy, 25, null);
        if (postArrayJson == null) {
            return null;
        }

        ArrayList<RedditPost> postList = new ArrayList<>();
        for (int i = 0; i < postArrayJson.length(); i++) {
            RedditPost post = extractPost(postArrayJson.getJSONObject(i).getJSONObject("data"));
            postList.add(post);
        }
        return postList;
    }

    private RedditPost retrievePostRaw(long guildId, String subreddit, String orderBy, String after, int page) {
        JSONArray postArrayJson = retrievePostArray(subreddit, orderBy, 100, after);
        RedditCache redditCache = new RedditCache(jedisPool, guildId, subreddit, orderBy);
        if (postArrayJson == null) {
            if (page > 0) {
                redditCache.removeFirst();
                return retrievePostRaw(guildId, subreddit, orderBy, null, 0);
            } else {
                return null;
            }
        }

        JSONArray filteredPostArrayJson = redditCache.filter(postArrayJson);
        if (filteredPostArrayJson.isEmpty()) {
            if (page < MAX_PAGE) {
                String newAfter = postArrayJson.getJSONObject(postArrayJson.length() - 1)
                        .getJSONObject("data")
                        .getString("name");
                return retrievePostRaw(guildId, subreddit, orderBy, newAfter, page + 1);
            } else {
                redditCache.removeFirst();
                return retrievePostRaw(guildId, subreddit, orderBy, null, 0);
            }
        } else {
            JSONObject dataJson = filteredPostArrayJson.getJSONObject(0).getJSONObject("data");
            RedditPost redditPost = extractPost(dataJson);
            redditCache.add(redditPost.getId());
            return redditPost;
        }
    }

    private JSONArray retrievePostArray(String subreddit, String orderBy, int limit, String after) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (jedis.exists(KEY_REDDIT_BLOCK)) {
                return null;
            }
        }

        JSONObject tempJson = retrieveRootData(subreddit, orderBy, limit, after);
        if (tempJson == null) {
            return null;
        }

        JSONArray postArrayJson = filterPostArrayStickied(tempJson.getJSONArray("children"));
        if (postArrayJson.length() <= 0) {
            return null;
        }

        return postArrayJson;
    }

    private JSONObject retrieveRootData(String subreddit, String orderBy, int limit, String after) {
        String url = "https://www.reddit.com/r/" + subreddit + "/" + orderBy + ".json?raw_json=1&limit=" + limit;
        if (after != null) {
            url += "&after=" + after;
        }
        HttpResponse httpResponse = webCache.get(url, 14);
        if (httpResponse.getCode() / 100 != 2) {
            LOGGER.error("Error code {} for subreddit {}", httpResponse.getCode(), subreddit);
            return null;
        }

        String body = httpResponse.getBody();
        if (!body.startsWith("{")) {
            return null;
        }

        JSONObject root = new JSONObject(body);
        if (root.has("error") && root.getInt("error") == 429) {
            try (Jedis jedis = jedisPool.getResource()) {
                SetParams params = new SetParams();
                params.ex(Duration.ofMinutes(TIMEOUT_MIN).toSeconds());
                jedis.set(KEY_REDDIT_BLOCK, Instant.now().toString(), params);
                return null;
            }
        }

        return root.getJSONObject("data");
    }

    private JSONArray filterPostArrayStickied(JSONArray postArrayJson) {
        JSONArray newArray = new JSONArray();
        for (int i = 0; i < postArrayJson.length(); i++) {
            JSONObject entry = postArrayJson.getJSONObject(i);
            JSONObject data = entry.getJSONObject("data");
            if (!data.has("stickied") || !data.getBoolean("stickied")) {
                newArray.put(entry);
            }
        }

        return newArray;
    }

    private RedditPost extractPost(JSONObject dataJson) {
        RedditPost post = new RedditPost();

        String description;
        String url;
        String source;
        String thumbnail;
        String domain = "";
        Object flair;

        post.setId(dataJson.getString("name"));
        if (dataJson.has("subreddit_name_prefixed")) post.setSubreddit(dataJson.getString("subreddit_name_prefixed"));
        post.setScore(dataJson.has("score") ? dataJson.getInt("score") : 0);
        post.setComments(dataJson.has("num_comments") ? dataJson.getInt("num_comments") : 0);
        post.setInstant(new Date(dataJson.getLong("created_utc") * 1000L).toInstant());

        if (dataJson.has("over_18")) {
            post.setNsfw(dataJson.getBoolean("over_18"));
        } else {
            post.setNsfw(true);
        }
        post.setTitle(dataJson.getString("title"));
        post.setAuthor(dataJson.getString("author"));

        flair = dataJson.get("link_flair_text");
        if (flair != null && !("" + flair).equals("null") && !("" + flair).equals("") && !("" + flair).equals(" ")) {
            post.setFlair(flair.toString());
        }
        description = dataJson.getString("selftext");
        url = dataJson.getString("url");
        post.setUrl(url);
        source = "https://www.reddit.com" + dataJson.getString("permalink");
        thumbnail = dataJson.getString("thumbnail");
        if (url.contains("//")) {
            domain = url.split("//")[1].replace("www.", "");
            if (domain.contains("/")) domain = domain.split("/")[0];
        }
        boolean postSource = true;

        if (dataJson.has("post_hint") && dataJson.getString("post_hint").equals("image")) {
            post.setImage(url);
            post.setUrl(source);
            postSource = false;
            domain = "reddit.com";
        } else {
            if (dataJson.has("preview") && dataJson.getJSONObject("preview").has("images")) {
                post.setThumbnail(dataJson.getJSONObject("preview").getJSONArray("images").getJSONObject(0).getJSONObject("source").getString("url"));
            } else {
                if (InternetUtil.urlContainsImage(url)) {
                    post.setImage(url);
                    post.setUrl(source);
                    postSource = false;
                    domain = "reddit.com";
                } else if (thumbnail.toLowerCase().startsWith("http")) {
                    post.setThumbnail(thumbnail);
                }
            }
        }

        if (postSource && !source.equals(url)) {
            post.setSourceLink(source);
        }

        post.setDescription(description);
        post.setDomain(domain);

        return post;
    }

}
