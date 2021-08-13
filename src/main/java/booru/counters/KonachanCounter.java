package booru.counters;

import core.WebCache;
import util.InternetUtil;

public class KonachanCounter extends SearchCounter {

    @Override
    public int count(WebCache webCache, String tags) {
        String url = "https://konachan.com/post.xml?limit=1&tags=" + InternetUtil.escapeForURL(tags);
        return countSearch(webCache, url);
    }

}
