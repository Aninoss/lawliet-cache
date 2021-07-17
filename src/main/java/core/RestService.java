package core;

import java.io.IOException;
import booru.BooruDownloader;
import booru.BooruImage;
import booru.BooruRequest;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

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
    private final BooruDownloader booruDownloader = new BooruDownloader(webCache, jedisPool);
    private final RandomPicker randomPicker = new RandomPicker(jedisPool, lockManager);

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
        try {
            return booruDownloader.getPicture(
                    booruRequest.getGuildId(),
                    booruRequest.getDomain(),
                    booruRequest.getSearchTerm(),
                    booruRequest.getSearchTermExtra(),
                    booruRequest.getImageTemplate(),
                    booruRequest.getAnimatedOnly(),
                    booruRequest.getCanBeVideo(),
                    booruRequest.getExplicit(),
                    booruRequest.getFilters(),
                    booruRequest.getSkippedResults()
            ).orElse(null);
        } catch (Throwable e) {
            LOGGER.error("Error in /booru", e);
            throw e;
        }
    }

    @POST
    @Path("/webcache")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public HttpResponse webcache(String url) throws IOException {
        try {
            return webCache.get(url);
        } catch (Throwable e) {
            LOGGER.error("Error in /webcache", e);
            throw e;
        }
    }

    @POST
    @Path("/random")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Integer randomPicker(RandomPickerRequest randomPickerRequest) {
        try {
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
