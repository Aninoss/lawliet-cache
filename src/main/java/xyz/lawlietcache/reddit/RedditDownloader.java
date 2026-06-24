package xyz.lawlietcache.reddit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import xyz.lawlietcache.core.HttpResponse;
import xyz.lawlietcache.core.JedisPoolManager;
import xyz.lawlietcache.core.WebCache;
import xyz.lawlietcache.reddit.exception.RedditException;
import xyz.lawlietcache.reddit.exception.SilentRedditException;
import xyz.lawlietcache.util.InternetUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RedditDownloader {

    private final static Logger LOGGER = LoggerFactory.getLogger(RedditDownloader.class);
    private final static String KEY_REDDIT_BLOCK = "reddit_block";
    private final static int TIMEOUT_MIN = 60;
    private final static int MAX_PAGE = 2;

    private final WebCache webCache;
    private final JedisPool jedisPool;

    public RedditDownloader(WebCache webCache, JedisPoolManager jedisPoolManager) {
        this.webCache = webCache;
        this.jedisPool = jedisPoolManager.get();
    }

    public RedditPost retrievePost(long guildId, String subreddit, String orderBy, boolean allowNsfw) throws RedditException {
        return retrievePostRaw(guildId, subreddit, orderBy, null, allowNsfw, 0);
    }

    public List<RedditPost> retrievePostsBulk(String subreddit, String orderBy) throws RedditException {
        JSONArray postArrayJson = retrievePostArray(subreddit, orderBy, 100, null);
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

    private RedditPost retrievePostRaw(long guildId, String subreddit, String orderBy, String after, boolean allowNsfw, int page) throws RedditException {
        JSONArray postArrayJson = retrievePostArray(subreddit, orderBy, 100, after);
        RedditCache redditCache = new RedditCache(jedisPool, guildId, subreddit, orderBy);
        if (postArrayJson == null) {
            if (page > 0) {
                redditCache.removeFirst();
                return retrievePostRaw(guildId, subreddit, orderBy, null, allowNsfw, 0);
            } else {
                return null;
            }
        }

        List<RedditPost> postList = new ArrayList<>();
        for (int i = 0; i < postArrayJson.length(); i++) {
            RedditPost post = extractPost(postArrayJson.getJSONObject(i).getJSONObject("data"));
            postList.add(post);
        }

        if (!allowNsfw && postList.stream().allMatch(RedditPost::isNsfw)) {
            return postList.get(0);
        }
        redditCache.filter(postList);
        postList.removeIf(redditPost -> !allowNsfw && redditPost.isNsfw());

        if (postList.isEmpty()) {
            if (page < MAX_PAGE) {
                String newAfter = postArrayJson.getJSONObject(postArrayJson.length() - 1)
                        .getJSONObject("data")
                        .getString("name");
                return retrievePostRaw(guildId, subreddit, orderBy, newAfter, allowNsfw, page + 1);
            } else {
                redditCache.removeFirst();
                return retrievePostRaw(guildId, subreddit, orderBy, null, allowNsfw, 0);
            }
        } else {
            RedditPost redditPost = postList.get(0);
            redditCache.add(redditPost.getId());
            return redditPost;
        }
    }

    private JSONArray retrievePostArray(String subreddit, String orderBy, int limit, String after) throws RedditException {
        try (Jedis jedis = jedisPool.getResource()) {
            if (jedis.exists(KEY_REDDIT_BLOCK)) {
                throw new SilentRedditException();
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

    private JSONObject retrieveRootData(String subreddit, String orderBy, int limit, String after) throws RedditException {
        String url = "https://www.reddit.com/r/" + subreddit + "/" + orderBy + ".json?raw_json=1&limit=" + limit;
        if (after != null) {
            url += "&after=" + after;
        }

        AtomicBoolean fromCache = new AtomicBoolean();
        HttpResponse httpResponse = webCache.get(url, 119, fromCache);
        if (httpResponse.getCode() / 100 != 2) {
            LOGGER.error("Error code {} for subreddit {} (from cache: {})", httpResponse.getCode(), subreddit, fromCache.get());
            if (httpResponse.getCode() == 404 || httpResponse.getCode() == 403) {
                return null;
            } else {
                throw new SilentRedditException();
            }
        }

        String body = httpResponse.getBody();
        if (!body.startsWith("{")) {
            throw new RedditException("Invalid body for subreddit " + subreddit);
        }

        JSONObject root = new JSONObject(body);
        if (root.has("error") && root.getInt("error") == 429) {
            try (Jedis jedis = jedisPool.getResource()) {
                SetParams params = new SetParams();
                params.ex(Duration.ofMinutes(TIMEOUT_MIN).toSeconds());
                jedis.set(KEY_REDDIT_BLOCK, Instant.now().toString(), params);
                throw new RedditException("Too many requests");
            }
        }

        return root.getJSONObject("data");
    }

    private JSONArray filterPostArrayStickied(JSONArray postArrayJson) {
        JSONArray newArray = new JSONArray();
        for (int i = 0; i < postArrayJson.length(); i++) {
            JSONObject entry = postArrayJson.getJSONObject(i);
            JSONObject data = entry.getJSONObject("data");
            if ((!data.has("stickied") || !data.getBoolean("stickied")) && data.has("author")) {
                newArray.put(entry);
            }
        }

        return newArray;
    }

    private RedditPost extractPost(JSONObject dataJson) {
        RedditPost post = new RedditPost();

        post.setId(dataJson.getString("name"));
        if (dataJson.has("subreddit_name_prefixed")) {
            post.setSubreddit(dataJson.getString("subreddit_name_prefixed"));
        }
        post.setScore(dataJson.has("score") ? dataJson.getInt("score") : 0);
        post.setComments(dataJson.has("num_comments") ? dataJson.getInt("num_comments") : 0);
        post.setInstant(new Date(dataJson.getLong("created_utc") * 1000L).toInstant());

        if (dataJson.has("over_18")) {
            post.setNsfw(dataJson.getBoolean("over_18"));
        } else {
            post.setNsfw(true);
        }
        post.setTitle(dataJson.getString("title"));
        post.setDescription(dataJson.getString("selftext"));
        post.setAuthor(dataJson.getString("author"));

        String redditUrl = "https://www.reddit.com" + dataJson.getString("permalink");
        post.setRedditUrl(redditUrl);
        post.setUrl(redditUrl);
        post.setDomain("reddit.com");

        Object flair = dataJson.get("link_flair_text");
        String flairText = flair instanceof String ? (String) flair : "";
        if (!flairText.equals("null") && !flairText.isBlank()) {
            post.setFlair(flair.toString());
        }

        String url = dataJson.getString("url");
        String postHint = dataJson.has("post_hint") ? dataJson.getString("post_hint") : "";
        if (dataJson.has("is_gallery") && dataJson.getBoolean("is_gallery") && dataJson.has("gallery_data") && dataJson.get("gallery_data") instanceof JSONObject) {
            JSONArray itemsJson = dataJson.getJSONObject("gallery_data").getJSONArray("items");
            JSONObject mediaMetadataJson = dataJson.getJSONObject("media_metadata");
            ArrayList<String> imageUrls = new ArrayList<>();
            for (int i = 0; i < itemsJson.length(); i++) {
                String mediaId = itemsJson.getJSONObject(i).getString("media_id");
                if (!mediaMetadataJson.has(mediaId) || !mediaMetadataJson.getJSONObject(mediaId).has("s")) {
                    continue;
                }

                JSONObject mediaJson = mediaMetadataJson.getJSONObject(mediaId).getJSONObject("s");
                String imageUrl = null;
                if (mediaJson.has("mp4")) {
                    imageUrl = mediaJson.getString("mp4");
                } else if (mediaJson.has("gif")) {
                    imageUrl = mediaJson.getString("gif");
                } else if (mediaJson.has("u")) {
                    imageUrl = mediaJson.getString("u");
                }

                if (imageUrl != null) {
                    imageUrls.add(imageUrl);
                }
            }

            if (!imageUrls.isEmpty()) {
                post.setMediaUrls(imageUrls);
                post.setImage(imageUrls.get(0));
            }
        } else if (postHint.equals("image")) {
            post.setMediaUrls(List.of(url));
            post.setImage(url);
        } else if (url.contains("redgifs.com") && dataJson.has("secure_media") && dataJson.get("secure_media") instanceof JSONObject && dataJson.getJSONObject("secure_media").getJSONObject("oembed").has("thumbnail_url")) {
            String imageUrl = dataJson.getJSONObject("secure_media").getJSONObject("oembed").getString("thumbnail_url").replace("-poster.jpg", ".mp4");
            post.setMediaUrls(List.of(imageUrl));
            post.setImage(imageUrl);
            post.setContentUrl(url);
        } else if (InternetUtil.urlContainsImage(url) || InternetUtil.urlContainsVideo(url)) {
            post.setMediaUrls(List.of(url));
            post.setImage(url);
        } else if (!url.equals(redditUrl)) {
            post.setContentUrl(url);
        }

        String thumbnail = "";
        if (dataJson.has("preview") && dataJson.getJSONObject("preview").has("images")) {
            thumbnail = dataJson.getJSONObject("preview").getJSONArray("images").getJSONObject(0).getJSONObject("source").getString("url");
        } else if (dataJson.has("thumbnail") && dataJson.get("thumbnail") instanceof String && dataJson.getString("thumbnail").startsWith("http")) {
            thumbnail = dataJson.getString("thumbnail");
        }
        post.setThumbnail(thumbnail);

        return post;
    }

}
