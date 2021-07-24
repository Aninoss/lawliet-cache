package booru.counters;

import core.WebCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtil;

public abstract class SearchCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(SearchCounter.class);

    protected int countSearch(WebCache webCache, String url) {
        String data;
        try {
            data = webCache.get(url, 15).getBody();
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", url.split("/")[2]);
            return 0;
        }

        if (data.contains("count=\"")) {
            return Integer.parseInt(StringUtil.extractGroups(data, "count=\"", "\"")[0]);
        } else {
            return 0;
        }
    }



}
