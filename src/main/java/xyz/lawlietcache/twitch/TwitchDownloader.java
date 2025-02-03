package xyz.lawlietcache.twitch;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import xyz.lawlietcache.core.JedisPoolManager;
import xyz.lawlietcache.core.WebCache;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.SetParams;
import xyz.lawlietcache.util.InternetUtil;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TwitchDownloader {

    private final static Logger LOGGER = LoggerFactory.getLogger(TwitchDownloader.class);
    private final static String[] TWITCH_CLIENT_IDS = System.getenv("TWITCH_CLIENT_IDS").split(",");
    private final static String KEY_TWITCH_BLOCK = "twitch_block";
    private final static String KEY_TWITCH_BEARER = "twitch_bearer:";
    private final static String KEY_TWITCH_USER = "twitch_user:";
    private final static String KEY_TWITCH_CLIENT_INDEX = "twitch_client_index";
    private final static String KEY_TWITCH_STREAMS = "twitch_streams";
    private final static String KEY_TWITCH_STREAM = "twitch_stream:";
    private final static String KEY_TWITCH_SCHEDULER_LOCK = "twitch_scheduler_lock";

    private final WebCache webCache;
    private final JedisPool jedisPool;
    private final Random random = new Random();

    public TwitchDownloader(WebCache webCache, JedisPoolManager jedisPoolManager) {
        this.webCache = webCache;
        this.jedisPool = jedisPoolManager.get();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                SetParams params = new SetParams();
                params.ex(Duration.ofMinutes(4).toSeconds());
                params.nx();
                String res = jedis.set(KEY_TWITCH_SCHEDULER_LOCK, "true", params);
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

    private void schedulerTask() throws IOException {
        LOGGER.info("Starting scheduler task");
        Set<String> userIds;
        try (Jedis jedis = jedisPool.getResource()) {
            userIds = jedis.zrangeByScore(
                    KEY_TWITCH_STREAMS,
                    String.valueOf(Instant.now().minus(Duration.ofMinutes(7)).getEpochSecond()),
                    "inf"
            );
        }

        LOGGER.info("Polling {} users", userIds.size());
        List<TwitchStream> streamList = retrieveStreamsRaw(new ArrayList<>(userIds));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            for (TwitchStream twitchStream : streamList) {
                String userId = twitchStream.getUserId();
                String twitchStreamKey = KEY_TWITCH_STREAM + userId;

                SetParams params = new SetParams();
                params.ex(Duration.ofMinutes(7).toSeconds());
                pipeline.set(twitchStreamKey, mapper.writeValueAsString(twitchStream), params);
            }
            pipeline.sync();
        }
    }

    public TwitchStream retrieveStream(String name) throws IOException {
        String twitchUserKey = KEY_TWITCH_USER + name;
        String twitchUserJson;
        TwitchUser twitchUser;

        try (Jedis jedis = jedisPool.getResource()) {
            twitchUserJson = jedis.get(twitchUserKey);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        if (twitchUserJson != null) {
            twitchUser = mapper.readValue(twitchUserJson, TwitchUser.class);
        } else {
            twitchUser = retrieveUserRaw(name);
            try (Jedis jedis = jedisPool.getResource()) {
                SetParams params = new SetParams();
                if (twitchUser != null) {
                    params.ex(Duration.ofHours(3 * 24 + random.nextInt(7 * 24)).toSeconds());
                } else {
                    params.ex(Duration.ofHours(1).toSeconds());
                }
                jedis.set(twitchUserKey, mapper.writeValueAsString(twitchUser), params);
            }
        }

        if (twitchUser != null) {
            return retrieveStream(twitchUser);
        } else {
            return null;
        }
    }

    public TwitchStream retrieveStream(TwitchUser twitchUser) throws IOException {
        String userId = twitchUser.getUserId();
        String twitchStreamKey = KEY_TWITCH_STREAM + userId;
        String twitchStreamJson;
        TwitchStream twitchStream;

        try (Jedis jedis = jedisPool.getResource()) {
            twitchStreamJson = jedis.get(twitchStreamKey);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        if (twitchStreamJson != null) {
            twitchStream = mapper.readValue(twitchStreamJson, TwitchStream.class);
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.zadd(KEY_TWITCH_STREAMS, Instant.now().getEpochSecond(), userId);
            }
        } else {
            twitchStream = retrieveStreamsRaw(List.of(userId)).get(0);
            try (Jedis jedis = jedisPool.getResource()) {
                SetParams params = new SetParams();
                params.ex(Duration.ofMinutes(7).toSeconds());
                jedis.set(twitchStreamKey, mapper.writeValueAsString(twitchStream), params);
                jedis.zadd(KEY_TWITCH_STREAMS, Instant.now().getEpochSecond(), userId);
            }
        }

        return twitchStream
                .setTwitchUser(twitchUser);
    }

    private TwitchUser retrieveUserRaw(String name) throws IOException {
        String url = "https://api.twitch.tv/helix/users?login=" + InternetUtil.escapeForURL(name);
        String response = retrieveApi(url);
        JSONArray usersArrayJson = new JSONObject(response).getJSONArray("data");
        if (!usersArrayJson.isEmpty()) {
            JSONObject userJson = usersArrayJson.getJSONObject(0);
            return new TwitchUser()
                    .setUserId(userJson.getString("id"))
                    .setLogin(userJson.getString("login"))
                    .setProfileImageUrl(userJson.getString("profile_image_url"))
                    .setDisplayName(userJson.getString("display_name"));
        } else {
            return null;
        }
    }

    private List<TwitchStream> retrieveStreamsRaw(List<String> userIds) throws IOException {
        HashMap<String, JSONObject> streamMap = new HashMap<>();
        int maxPage = (userIds.size() - 1) / 100;
        for (int page = 0; page <= maxPage; page++) {
            StringBuilder urlBuilder = new StringBuilder("https://api.twitch.tv/helix/streams?first=100");
            for (int i = 0; i < 100 && (page * 100 + i) < userIds.size(); i++) {
                urlBuilder.append("&")
                        .append("user_id=").append(userIds.get(page * 100 + i));
            }
            String response = retrieveApi(urlBuilder.toString());
            JSONArray streamsArrayJson = new JSONObject(response).getJSONArray("data");
            for (int i = 0; i < streamsArrayJson.length(); i++) {
                JSONObject streamJson = streamsArrayJson.getJSONObject(i);
                streamMap.put(streamJson.getString("user_id"), streamJson);
            }
        }

        return userIds.stream()
                .map(userId -> {
                    JSONObject streamJson = streamMap.get(userId);
                    if (streamJson != null) {
                        return new TwitchStream()
                                .setLive(true)
                                .setUserId(userId)
                                .setThumbnailUrl(
                                        streamJson.getString("thumbnail_url")
                                                .replace("{width}", "1280")
                                                .replace("{height}", "720") + "?" + System.nanoTime())
                                .setGame(streamJson.getString("game_name"))
                                .setTitle(streamJson.getString("title"))
                                .setViewers(streamJson.getInt("viewer_count"));
                    } else {
                        return new TwitchStream()
                                .setLive(false)
                                .setUserId(userId);
                    }
                })
                .collect(Collectors.toList());
    }

    private String retrieveApi(String url) throws IOException {
        return retrieveApi(url, 3);
    }

    private String retrieveApi(String url, int bearerTries) throws IOException {
        String clientId;
        String bearer;
        try (Jedis jedis = jedisPool.getResource()) {
            if (jedis.exists(KEY_TWITCH_BLOCK)) {
                throw new IOException("Twitch: too many requests");
            }
            int clientIdIndex = (int) (jedis.incr(KEY_TWITCH_CLIENT_INDEX) % TWITCH_CLIENT_IDS.length);
            clientId = TWITCH_CLIENT_IDS[clientIdIndex];
            bearer = jedis.get(KEY_TWITCH_BEARER + clientId);
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Client-ID", clientId)
                .addHeader("Authorization", "Bearer " + bearer)
                .build();

        try (Response response = webCache.getClient().newCall(request).execute()) {
            if (response.code() / 100 == 2) {
                return response.body().string();
            } else if (response.code() == 401) { /* generates access token */
                try (Jedis jedis = jedisPool.getResource()) {
                    SetParams params = new SetParams();
                    params.ex(Duration.ofDays(30).toSeconds());
                    jedis.set(KEY_TWITCH_BEARER + clientId, generateBearerToken(clientId), params);
                }
                if (bearerTries > 0) {
                    return retrieveApi(url, bearerTries - 1);
                } else {
                    throw new IOException("Twitch bearer token error");
                }
            } else if (response.code() == 429) { /* too many requests */
                long unixTime = System.currentTimeMillis() / 1000L;
                long ratelimitReset = Long.parseLong(response.header("Ratelimit-Reset")) + 5;
                try (Jedis jedis = jedisPool.getResource()) {
                    SetParams params = new SetParams();
                    params.ex(ratelimitReset - unixTime);
                    jedis.set(KEY_TWITCH_BLOCK, Instant.now().toString(), params);
                }
            }
            throw new IOException("Twitch connection error " + response.code() + ": " + response.body().string());
        }
    }

    private String generateBearerToken(String clientId) throws IOException {
        String url = "https://id.twitch.tv/oauth2/token" +
                "?client_id=" + clientId +
                "&client_secret=" + System.getenv("TWITCH_SECRET_" + clientId) +
                "&grant_type=client_credentials";

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0]))
                .build();

        try (Response response = webCache.getClient().newCall(request).execute()) {
            if (response.code() / 100 == 2) {
                JSONObject responseJson = new JSONObject(response.body().string());
                return responseJson.getString("access_token");
            } else {
                throw new IOException("Twitch token request connection error " + response.code() + ": " + response.body().string());
            }
        }
    }

}
