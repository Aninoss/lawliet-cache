package booru.counters;

import core.WebCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtil;

public abstract class FurryCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(FurryCounter.class);

    protected int countFurry(WebCache webCache, String url, boolean withCache) {
        String domain = url.split("/")[2];
        String data;
        try {
            if (withCache) {
                data = webCache.get(url, 60).getBody();
            } else {
                data = webCache.getWithoutCache(url).getBody();
            }
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", domain, e);
            return 0;
        }

        if (data == null) {
            LOGGER.error("Error for domain {}: empty data", domain);
            return 0;
        }

        int posts = StringUtil.countMatches(data, "<article id=\"post_");
        String[] groups = StringUtil.extractGroups(data, "<div class=\"paginator\">", "</menu></div>");
        if (groups.length == 0) {
            LOGGER.error("Error for domain {}: empty groups", domain);
            return 0;
        }

        String paginator = groups[0];
        String[] pageNumbers = StringUtil.extractGroups(paginator, ">", "<");

        int pageMax = 0;
        for (String pageNumber : pageNumbers) {
            if (StringUtil.stringIsInt(pageNumber)) {
                int n = Integer.parseInt(pageNumber);
                pageMax = Math.max(n, pageMax);
            }
        }

        return pageMax == 1 ? posts : Math.max((pageMax - 1) * 26, 0);
    }

}
