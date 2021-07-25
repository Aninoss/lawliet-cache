package booru.counters;

import core.WebCache;
import util.InternetUtil;

public class RealbooruCounter extends SearchCounter {

    @Override
    public int count(WebCache webCache, String tags) {
        String url = "https://realbooru.com/index.php?page=dapi&s=post&q=index&json=0&limit=1&tags=" + InternetUtil.escapeForURL(tags);
        return countSearch(webCache, url);
    }

}