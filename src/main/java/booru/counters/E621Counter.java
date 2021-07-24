package booru.counters;

import core.WebCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.InternetUtil;
import util.StringUtil;

public class E621Counter extends SearchCounter {

    private final static Logger LOGGER = LoggerFactory.getLogger(E621Counter.class);

    @Override
    public int count(WebCache webCache, String tags) {
        String url = "https://e621.net/posts?page=1&limit=26&tags=" + InternetUtil.escapeForURL(tags);
        String data;
        try {
            data = webCache.get(url, 15).getBody();
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", url.split("/")[2]);
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
