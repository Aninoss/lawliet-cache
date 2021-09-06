package booru.counters;

import core.WebCache;
import util.InternetUtil;

public class E621Counter extends FurryCounter {

    @Override
    public int count(WebCache webCache, String tags) {
        String url = "https://e621.net/posts?page=1&limit=26&tags=" + InternetUtil.escapeForURL(tags + " status:active");
        return countFurry(webCache, url);
    }

}
