package booru.counters;

import core.WebCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtil;

public abstract class SearchCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(SearchCounter.class);

    protected int countSearch(WebCache webCache, String url, boolean withCache) {
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

        if (data.contains("count=\"")) {
            return Integer.parseInt(StringUtil.extractGroups(data, "count=\"", "\"")[0]);
        } else {
            return -1;
        }
    }



}
