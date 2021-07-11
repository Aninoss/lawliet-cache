package core;

import java.util.concurrent.ExecutionException;
import booru.BooruDownloader;
import booru.BooruImage;
import booru.BooruRequest;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("")
@Singleton
public class RestService {

    private final static Logger LOGGER = LoggerFactory.getLogger(RestService.class);

    private final BooruDownloader booruDownloader = new BooruDownloader();

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
    public BooruImage booru(BooruRequest booruRequest) throws ExecutionException {
        try {
            return booruDownloader.getPicture(
                    booruRequest.getGuildId(),
                    booruRequest.getDomain(),
                    booruRequest.getSearchTerm(),
                    booruRequest.getSearchTermExtra(),
                    booruRequest.getImageTemplate(),
                    booruRequest.isAnimatedOnly(),
                    booruRequest.canBeVideo(),
                    booruRequest.isExplicit(),
                    booruRequest.getFilters(),
                    booruRequest.getSkippedResults()
            ).orElse(null);
        } catch (Throwable e) {
            LOGGER.error("Error in /booru", e);
            throw e;
        }
    }

}
