package booru.counters;

import core.WebCache;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.InternetUtil;

public class DanbooruCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(DanbooruCounter.class);

    @Override
    public int count(WebCache webCache, String tags) {
        String url = "https://danbooru.donmai.us/counts/posts.json?tags=" + InternetUtil.escapeForURL(tags + " status:active");
        String data;
        try {
            data = webCache.get(url, 15).getBody();
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", url.split("/")[2], e);
            return 0;
        }

        JSONObject json;
        try {
            json = new JSONObject(data).getJSONObject("counts");
        } catch (JSONException e) {
            LOGGER.error("Danbooru invalid counter response");
            return 0; //TODO
        }


        if (json.has("posts") && !json.isNull("posts")) {
            return json.getInt("posts");
        } else {
            return 0;
        }
    }

}
