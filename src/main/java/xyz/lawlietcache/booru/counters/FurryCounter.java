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

        int posts = StringUtil.countMatches(data, "<article id=\"post_");
        String[] groups = StringUtil.extractGroups(data, "<div class=\"paginator\">", "</menu></div>");
        if (groups.length == 0) {
            return -1;
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

        return pageMax == 1 ? posts : Math.max((pageMax - 1) * posts, 0);
    }

}
