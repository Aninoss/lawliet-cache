package xyz.lawlietcache.reddit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import xyz.lawlietcache.core.HttpResponse;
import xyz.lawlietcache.core.JedisPoolManager;
import xyz.lawlietcache.reddit.exception.RedditException;
import xyz.lawlietcache.reddit.exception.SilentRedditException;
import xyz.lawlietcache.util.InternetUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class RedditDownloader {

    private final static Logger LOGGER = LoggerFactory.getLogger(RedditDownloader.class);
    private final static String KEY_REDDIT_BLOCK = "reddit_block";
    private final static int BILLING_BLOCK_MIN = 10;
    private final static int MAX_PAGE = 2;

    private final RedditApisClient redditApisClient;
    private final JedisPool jedisPool;

    public RedditDownloader(RedditApisClient redditApisClient, JedisPoolManager jedisPoolManager) {
        this.redditApisClient = redditApisClient;
        this.jedisPool = jedisPoolManager.get();
    }

    public List<SubredditAutoComplete> getAutoComplete(String query) {
        if (query.isEmpty() || query.length() > 25) {
            return Collections.emptyList();
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", query);
        params.put("limit", "10");
        params.put("nsfw", "true");

        JSONObject root;
        try {
            root = requestJson("/api/reddit/typeahead", params, (int) Duration.ofHours(24).toMinutes(), "autocomplete " + query);
        } catch (RedditException e) {
            return Collections.emptyList();
        }

        JSONArray arrayJson = root != null ? root.optJSONArray("subreddits") : null;
        if (arrayJson == null) {
            return Collections.emptyList();
        }

        ArrayList<SubredditAutoComplete> subreddits = new ArrayList<>();
        for (int i = 0; i < arrayJson.length(); i++) {
            JSONObject subredditJson = arrayJson.optJSONObject(i);
            if (subredditJson == null) {
                continue;
            }

            String name = subredditJson.optString("display_name_prefixed", "");
            if (name.isBlank() && !subredditJson.optString("name", "").isBlank()) {
                name = "r/" + subredditJson.getString("name");
            }
            if (name.isBlank()) {
                continue;
            }

            SubredditAutoComplete autoComplete = new SubredditAutoComplete()
                    .setName(name)
                    .setSubscribers(subredditJson.optLong("subscribers", 0))
                    .setNsfw(subredditJson.optBoolean("over_18", false));
            subreddits.add(autoComplete);
        }

        subreddits.sort((s1, s2) -> Long.compare(s2.getSubscribers(), s1.getSubscribers()));
        return Collections.unmodifiableList(subreddits);
    }

    public RedditPost retrievePost(long guildId, String subreddit, String orderBy, boolean allowNsfw) throws RedditException {
        return retrievePostRaw(guildId, subreddit, orderBy, null, allowNsfw, 0);
    }

    public List<RedditPost> retrievePostsBulk(String subreddit, String orderBy) throws RedditException {
        Listing listing = retrieveListing(subreddit, orderBy, null);
        if (listing == null) {
            return null;
        }

        ArrayList<RedditPost> postList = new ArrayList<>();
        for (JSONObject postJson : listing.posts()) {
            postList.add(extractPost(postJson));
        }
        return postList;
    }

    private RedditPost retrievePostRaw(long guildId, String subreddit, String orderBy, String after, boolean allowNsfw, int page) throws RedditException {
        Listing listing = retrieveListing(subreddit, orderBy, after);
        RedditCache redditCache = new RedditCache(jedisPool, guildId, subreddit, orderBy);
        if (listing == null) {
            if (page > 0) {
                redditCache.removeFirst();
                return retrievePostRaw(guildId, subreddit, orderBy, null, allowNsfw, 0);
            } else {
                return null;
            }
        }

        List<RedditPost> postList = new ArrayList<>();
        for (JSONObject postJson : listing.posts()) {
            postList.add(extractPost(postJson));
        }

        if (!allowNsfw && postList.stream().allMatch(RedditPost::isNsfw)) {
            return postList.get(0);
        }
        redditCache.filter(postList);
        postList.removeIf(redditPost -> !allowNsfw && redditPost.isNsfw());

        if (postList.isEmpty()) {
            if (page < MAX_PAGE && listing.after() != null) {
                return retrievePostRaw(guildId, subreddit, orderBy, listing.after(), allowNsfw, page + 1);
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

    private Listing retrieveListing(String subreddit, String orderBy, String after) throws RedditException {
        try (Jedis jedis = jedisPool.getResource()) {
            if (jedis.exists(KEY_REDDIT_BLOCK)) {
                throw new SilentRedditException();
            }
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("subreddit", subreddit);
        params.put("sort", orderBy);
        if (orderBy.equals("top") || orderBy.equals("controversial")) {
            params.put("t", "day");
        }
        params.put("limit", String.valueOf(100));
        if (after != null) {
            params.put("after", after);
        }

        JSONObject root = requestJson("/api/reddit/posts", params, 119, "subreddit " + subreddit);
        if (root == null) {
            return null;
        }

        JSONArray postArrayJson = root.optJSONArray("posts");
        if (postArrayJson == null) {
            return null;
        }

        List<JSONObject> posts = filterPostArray(postArrayJson);
        if (posts.isEmpty()) {
            return null;
        }

        return new Listing(posts, root.optString("after", null));
    }

    private JSONObject requestJson(String path, Map<String, String> params, int minutesCached, String logTarget) throws RedditException {
        AtomicBoolean fromCache = new AtomicBoolean();
        HttpResponse httpResponse = redditApisClient.get(path, params, minutesCached, fromCache);
        int code = httpResponse.getCode();
        String body = httpResponse.getBody() != null ? httpResponse.getBody() : "";

        if (code / 100 != 2) {
            LOGGER.error("Error code {} for {} (from cache: {})", code, logTarget, fromCache.get());
            if (code == 404) {
                return null;
            } else if (code == 402) {
                LOGGER.error("Redditapis balance is empty, blocking reddit requests for {} minutes", BILLING_BLOCK_MIN);
                blockRequests(Duration.ofMinutes(BILLING_BLOCK_MIN));
            } else if (code == 401 || (code == 403 && body.contains("Invalid token"))) {
                LOGGER.error("Redditapis rejected REDDITAPIS_KEY: {}", body);
            } else if (code == 403) {
                return null;
            }
            throw new SilentRedditException();
        }

        if (!body.startsWith("{")) {
            throw new RedditException("Invalid body for " + logTarget);
        }

        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            throw new RedditException("Invalid json for " + logTarget, e);
        }
    }

    private void blockRequests(Duration duration) {
        try (Jedis jedis = jedisPool.getResource()) {
            SetParams params = new SetParams();
            params.ex(duration.toSeconds());
            jedis.set(KEY_REDDIT_BLOCK, Instant.now().toString(), params);
        }
    }

    private List<JSONObject> filterPostArray(JSONArray postArrayJson) {
        List<JSONObject> posts = new ArrayList<>();
        for (int i = 0; i < postArrayJson.length(); i++) {
            JSONObject post = postArrayJson.optJSONObject(i);
            if (post == null) {
                continue;
            }

            boolean stickied = post.optBoolean("stickied", false);
            String author = post.optString("author", "");
            String linkUrl = post.optString("link_url", "");
            String permalink = post.optString("permalink", "");
            if (!stickied && !author.isEmpty() && (!linkUrl.startsWith("/r/") || !permalink.startsWith("/r/") || linkUrl.equals(permalink))) {
                posts.add(post);
            }
        }

        return posts;
    }

    private RedditPost extractPost(JSONObject dataJson) {
        RedditPost post = new RedditPost();

        String id = dataJson.optString("name", "");
        post.setId(!id.isEmpty() ? id : "t3_" + dataJson.getString("id"));

        String subreddit = dataJson.optString("subreddit", "");
        if (!subreddit.isEmpty()) {
            post.setSubreddit(subreddit.startsWith("r/") ? subreddit : "r/" + subreddit);
        }
        post.setScore(dataJson.optInt("upvotes", 0));
        post.setComments(dataJson.optInt("comments", 0));
        post.setInstant(Instant.ofEpochSecond((long) dataJson.getDouble("created_utc")));
        post.setNsfw(dataJson.optBoolean("over_18", false));
        post.setTitle(dataJson.getString("title"));
        post.setDescription(dataJson.optString("text", ""));
        post.setAuthor(dataJson.getString("author"));
        post.setRedditUrl(dataJson.getString("permalink"));

        String flairText = dataJson.optString("link_flair_text", "");
        if (!flairText.equals("null") && !flairText.isBlank()) {
            post.setFlair(flairText);
        }

        String thumbnail = "";
        JSONArray galleryJson = dataJson.optJSONArray("gallery_images");
        if (dataJson.optBoolean("is_gallery", false) && galleryJson != null && !galleryJson.isEmpty()) {
            ArrayList<String> imageUrls = new ArrayList<>();
            for (int i = 0; i < galleryJson.length(); i++) {
                JSONObject imageJson = galleryJson.optJSONObject(i);
                if (imageJson == null) {
                    continue;
                }

                String imageUrl = imageJson.optString("url", "");
                if (!imageUrl.isEmpty()) {
                    imageUrls.add(imageUrl);
                }
                if (thumbnail.isEmpty()) {
                    thumbnail = imageJson.optString("preview_url", "");
                }
            }

            if (!imageUrls.isEmpty()) {
                post.setMediaUrls(imageUrls);
            }
        } else {
            String url = dataJson.optString("link_url", "");
            if (url.isEmpty()) {
                url = dataJson.optString("url", "");
            }

            if (url.contains("i.redd.it") || InternetUtil.urlContainsImage(url) || InternetUtil.urlContainsVideo(url)) {
                post.setMediaUrls(List.of(url));
            } else if (!url.isEmpty() && !url.startsWith("https://www.reddit.com") && !url.startsWith("https://reddit.com") && !url.startsWith("/")) {
                post.setContentUrl(url);
            }
        }

        if (thumbnail.isEmpty()) {
            thumbnail = dataJson.optString("thumbnail", "");
        }
        post.setThumbnail(thumbnail.startsWith("http") || thumbnail.startsWith("/") ? thumbnail : "");

        return post;
    }

    private record Listing(List<JSONObject> posts, String after) {
    }

}
