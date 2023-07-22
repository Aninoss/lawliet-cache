package core;

import booru.*;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pixiv.PixivDownloader;
import pixiv.PixivException;
import pixiv.PixivImage;
import pixiv.PixivRequest;
import reddit.RedditDownloader;
import reddit.RedditException;
import reddit.RedditPost;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import twitch.TwitchDownloader;
import twitch.TwitchStream;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Path("")
@Singleton
public class RestService {

    private final static Logger LOGGER = LoggerFactory.getLogger(RestService.class);

    private final JedisPool jedisPool = new JedisPool(
            buildPoolConfig(),
            System.getenv("REDIS_HOST"),
            Integer.parseInt(System.getenv("REDIS_PORT"))
    );
    private final LockManager lockManager = new LockManager();
    private final WebCache webCache = new WebCache(jedisPool, lockManager);
    private final RandomPicker randomPicker = new RandomPicker(jedisPool, lockManager);

    private final BooruDownloader booruDownloader = new BooruDownloader(webCache, jedisPool);
    private final RedditDownloader redditDownloader = new RedditDownloader(webCache, jedisPool);
    private final TwitchDownloader twitchDownloader = new TwitchDownloader(webCache, jedisPool);
    private final PixivDownloader pixivDownloader = new PixivDownloader(webCache, jedisPool);

    @GET
    @Path("/ping")
    @Produces(MediaType.TEXT_PLAIN)
    public String ping() {
        return "Pong!";
    }

    @POST
    @Path("/booru")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public BooruImage booru(BooruRequest booruRequest) throws BooruException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return booruDownloader.getPicture(
                    booruRequest.getGuildId(),
                    booruRequest.getDomain(),
                    booruRequest.getSearchTerm(),
                    booruRequest.getAnimatedOnly(),
                    booruRequest.getMustBeExplicit(),
                    booruRequest.getCanBeVideo(),
                    booruRequest.getFilters(),
                    booruRequest.getStrictFilters() != null ? booruRequest.getStrictFilters() : Collections.emptyList(),
                    booruRequest.getSkippedResults(),
                    booruRequest.getTest()
            );
        } catch (Throwable e) {
            if (e.getMessage() != null) {
                LOGGER.error("Error in /booru", e);
            }
            throw e;
        }
    }

    @GET
    @Path("/booru_autocomplete/{domain}/{search}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<BooruChoice> booruAutoComplete(@PathParam("domain") String domain, @PathParam("search") String search) throws BooruException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return booruDownloader.getTags(domain, search);
        } catch (Throwable e) {
            LOGGER.error("Error in /booru_autocomplete", e);
            throw e;
        }
    }

    @GET
    @Path("/reddit/single/{guild_id}/{nsfw_allowed}/{subreddit}/{order_by}")
    @Produces(MediaType.APPLICATION_JSON)
    public RedditPost redditSingle(@PathParam("guild_id") long guildId, @PathParam("nsfw_allowed") boolean nsfwAllowed,
                                   @PathParam("subreddit") String subreddit, @PathParam("order_by") String orderBy
    ) throws RedditException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return redditDownloader.retrievePost(guildId, subreddit, orderBy, nsfwAllowed);
        } catch (Throwable e) {
            if (e.getMessage() != null) {
                LOGGER.error("Error in /reddit (single)", e);
            }
            throw e;
        }
    }

    @GET
    @Path("/reddit/bulk/{subreddit}/{order_by}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<RedditPost> redditBulk(@PathParam("subreddit") String subreddit, @PathParam("order_by") String orderBy) throws RedditException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return redditDownloader.retrievePostsBulk(subreddit, orderBy);
        } catch (Throwable e) {
            if (e.getMessage() != null) {
                LOGGER.error("Error in /reddit (bulk)", e);
            }
            throw e;
        }
    }

    @POST
    @Path("/pixiv_single")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public PixivImage pixivSingle(PixivRequest pixivRequest) throws PixivException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return pixivDownloader.retrieveImage(
                    pixivRequest.getGuildId(),
                    pixivRequest.getWord(),
                    pixivRequest.isNsfwAllowed(),
                    pixivRequest.getFilters(),
                    pixivRequest.getStrictFilters()
            );
        } catch (Throwable e) {
            if (e.getMessage() != null) {
                LOGGER.error("Error in /pixiv_single", e);
            }
            throw e;
        }
    }

    @POST
    @Path("/pixiv_bulk")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public List<PixivImage> pixivBulk(PixivRequest pixivRequest) throws PixivException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return pixivDownloader.retrieveImagesBulk(
                    pixivRequest.getWord(),
                    pixivRequest.getFilters(),
                    pixivRequest.getStrictFilters()
            );
        } catch (Throwable e) {
            if (e.getMessage() != null) {
                LOGGER.error("Error in /pixiv_bulk", e);
            }
            throw e;
        }
    }

    @GET
    @Path("/twitch/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public TwitchStream redditBulk(@PathParam("name") String name) throws IOException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return twitchDownloader.retrieveStream(name);
        } catch (Throwable e) {
            LOGGER.error("Error in /twitch", e);
            throw e;
        }
    }

    @POST
    @Path("/webcache")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse webcache(String url) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return webCache.get(url, 5);
        } catch (Throwable e) {
            LOGGER.error("Error in /webcache", e);
            throw e;
        }
    }

    @GET
    @Path("/cached_proxy")
    public Response cachedProxy(@HeaderParam("X-Proxy-Url") String url,
                                @HeaderParam("X-Proxy-Minutes") int minutes
    ) {
        return requestCachedProxy("GET", url, minutes, null, null);
    }

    @POST
    @Path("/cached_proxy")
    public Response cachedProxy(@HeaderParam("X-Proxy-Url") String url,
                                @HeaderParam("X-Proxy-Minutes") int minutes,
                                @HeaderParam("Content-Type") String contentType,
                                String body
    ) {
        return requestCachedProxy("POST", url, minutes, contentType, body);
    }

    @POST
    @Path("/random")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Integer randomPicker(RandomPickerRequest randomPickerRequest) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            return randomPicker.pick(
                    randomPickerRequest.getTag(),
                    randomPickerRequest.getGuildId(),
                    randomPickerRequest.getSize()
            );
        } catch (Throwable e) {
            LOGGER.error("Error in /random", e);
            throw e;
        }
    }

    private Response requestCachedProxy(String method, String url, int minutes, String contentType, String body) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(30))) {
            HttpResponse httpResponse = webCache.request(method, url, body, contentType, minutes);
            if (httpResponse.getCode() / 100 == 2) {
                return Response.ok(httpResponse.getBody()).build();
            } else {
                return Response.status(httpResponse.getCode()).build();
            }
        } catch (Throwable e) {
            LOGGER.error("Error in /webcache", e);
            throw e;
        }
    }

    private JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(32);
        poolConfig.setMinIdle(0);
        return poolConfig;
    }

}
