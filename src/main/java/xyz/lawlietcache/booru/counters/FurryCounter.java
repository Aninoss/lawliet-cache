package xyz.lawlietcache.booru.counters;

import xyz.lawlietcache.core.WebCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.lawlietcache.util.StringUtil;

public abstract class FurryCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(FurryCounter.class);

    protected int countFurry(WebCache webCache, String url, boolean withCache) {
        String domain = url.split("/")[2];
        String data;
        try {
            if (withCache) {
                data = webCache.get(url, 1440).getBody();
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

        String[] groups = StringUtil.extractGroups(data, "class=\"approximate-count\"", "data-pages");
        if (groups.length != 1) {
            return -1;
        }

        String[] countGroups = StringUtil.extractGroups(groups[0], "data-count=\"", "\"");
        if (countGroups.length != 1) {
            return -1;
        }

        return Integer.parseInt(countGroups[0]);
    }

}
