package core;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import booru.BooruChoice;
import booru.BooruDownloader;
import booru.BooruImage;
import booru.BooruRequest;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reddit.RedditDownloader;
import reddit.RedditPost;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import twitch.TwitchDownloader;
import twitch.TwitchStream;

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
    private final CheckOwnConnection checkOwnConnection = new CheckOwnConnection(webCache.getClient());

    private final BooruDownloader booruDownloader = new BooruDownloader(webCache, jedisPool);
    private final RedditDownloader redditDownloader = new RedditDownloader(webCache, jedisPool);
    private final TwitchDownloader twitchDownloader = new TwitchDownloader(webCache, jedisPool);

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
    public BooruImage booru(BooruRequest booruRequest) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(10))) {
            return booruDownloader.getPicture(
                    booruRequest.getGuildId(),
                    booruRequest.getDomain(),
                    booruRequest.getSearchTerm(),
                    booruRequest.getAnimatedOnly(),
                    booruRequest.getExplicit(),
                    booruRequest.getCanBeVideo(),
                    booruRequest.getFilters(),
                    booruRequest.getSkippedResults(),
                    booruRequest.getTest()
            );
        } catch (Throwable e) {
            LOGGER.error("Error in /booru", e);
            throw e;
        }
    }

    @GET
    @Path("/booru_autocomplete/{domain}/{search}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<BooruChoice> booruAutoComplete(@PathParam("domain") String domain, @PathParam("search") String search) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(10))) {
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
    ) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(10))) {
            return redditDownloader.retrievePost(guildId, subreddit, orderBy, nsfwAllowed);
        } catch (Throwable e) {
            LOGGER.error("Error in /reddit (single)", e);
            throw e;
        }
    }

    @GET
    @Path("/reddit/bulk/{subreddit}/{order_by}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<RedditPost> redditBulk(@PathParam("subreddit") String subreddit, @PathParam("order_by") String orderBy) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(10))) {
            return redditDownloader.retrievePostsBulk(subreddit, orderBy);
        } catch (Throwable e) {
            LOGGER.error("Error in /reddit (bulk)", e);
            throw e;
        }
    }

    @GET
    @Path("/twitch/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public TwitchStream redditBulk(@PathParam("name") String name) throws IOException {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(10))) {
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
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(10))) {
            return webCache.get(url, 5);
        } catch (Throwable e) {
            LOGGER.error("Error in /webcache", e);
            throw e;
        }
    }

    @POST
    @Path("/cached_proxy/{minutes}")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response cachedProxy(String url, @PathParam("minutes") int minutes) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(10))) {
            HttpResponse httpResponse = webCache.get(url, minutes);
            if (httpResponse.getCode() / 100 == 2) {
                return Response.ok(httpResponse.getBody()).build();
            } else {
                return Response.status(httpResponse.getCode()).build();
            }
        } catch (Throwable e) {
            LOGGER.error("Error in /webcache_raw", e);
            throw e;
        }
    }

    @POST
    @Path("/random")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Integer randomPicker(RandomPickerRequest randomPickerRequest) {
        try (AsyncTimer timer = new AsyncTimer(Duration.ofSeconds(10))) {
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

    private JedisPoolConfig buildPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(32);
        poolConfig.setMinIdle(0);
        return poolConfig;
    }

}
