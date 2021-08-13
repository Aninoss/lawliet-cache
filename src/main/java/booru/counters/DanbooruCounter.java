package booru.counters;

import core.WebCache;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DanbooruCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(DanbooruCounter.class);

    @Override
    public int count(WebCache webCache, String tags) {
        String url = "https://danbooru.donmai.us/counts/posts.json?tags=" + tags;
        String data;
        try {
            data = webCache.get(url, 15).getBody();
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", url.split("/")[2], e);
            return 0;
        }

        JSONObject json = new JSONObject(data).getJSONObject("counts");
        if (json.has("posts") && !json.isNull("posts")) {
            return json.getInt("posts");
        } else {
            return 0;
        }
    }

}
