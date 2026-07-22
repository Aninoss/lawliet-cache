package xyz.lawlietcache.booru.counters;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.lawlietcache.core.WebCache;

public abstract class FurryCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(FurryCounter.class);

    protected int countFurry(WebCache webCache, String url, boolean withCache) {
        String domain = url.split("/")[2];
        String data;
        try {
            if (withCache) {
                data = webCache.get(url, 30).getBody();
            } else {
                data = webCache.getWithoutCache(url).getBody();
            }
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", domain, e);
            return -1;
        }

        if (data == null) {
            return -1;
        }

        try {
            return new JSONObject(data).getJSONArray("posts").length();
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", domain, e);
            return -1;
        }
    }

}
