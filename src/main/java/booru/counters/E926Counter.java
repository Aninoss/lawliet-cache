package booru.counters;

import core.WebCache;
import util.InternetUtil;

public class E926Counter extends FurryCounter {

    @Override
    public int count(WebCache webCache, String tags) {
        String url = "https://e926.net/posts?page=1&limit=26&tags=" + InternetUtil.escapeForURL(tags);
        return countFurry(webCache, url);
    }

}
