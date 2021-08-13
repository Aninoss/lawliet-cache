package booru.counters;

import core.WebCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtil;

public abstract class FurryCounter implements Counter {

    private final static Logger LOGGER = LoggerFactory.getLogger(FurryCounter.class);

    protected int countFurry(WebCache webCache, String url) {
        String data;
        try {
            data = webCache.get(url, 15).getBody();
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", url.split("/")[2], e);
            return 0;
        }

        int posts = StringUtil.countMatches(data, "<article id=\"post_");
        String paginator = StringUtil.extractGroups(data, "<div class=\"paginator\">", "</menu></div>")[0];
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
