package booru.counters;

import core.WebCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import util.InternetUtil;
import util.StringUtil;

public class RealbooruWorkaroundCounter extends SearchCounter {

    private final static Logger LOGGER = LoggerFactory.getLogger(DanbooruCounter.class);

    @Override
    public int count(WebCache webCache, JedisPool jedisPool, String tags, boolean withCache) {
        String url = "https://realbooru.com/index.php?page=post&s=list&tags=" + InternetUtil.escapeForURL(tags);
        String data;
        try {
            if (withCache) {
                data = webCache.get(url, 1440).getBody();
            } else {
                data = webCache.getWithoutCache(url).getBody();
            }
        } catch (Throwable e) {
            LOGGER.error("Error for domain {}", url.split("/")[2], e);
            return -1;
        }

        if (data == null) {
            if (tags.isEmpty() && !withCache) {
                LOGGER.error("Realbooru data is null");
            }
            return -1;
        }

        int posts = StringUtil.countMatches(data, "<div class=\"col thumb\"");
        if (posts == 0) {
            if (tags.isEmpty() && !withCache) {
                LOGGER.error("Realbooru no posts:\n{}", data);
            }
            return 0;
        }

        String[] groups = StringUtil.extractGroups(data, "<div id=\"paginator\">", "</div>");
        if (groups.length == 0) {
            if (tags.isEmpty() && !withCache) {
                LOGGER.error("Realbooru no groups:\n{}", data);
            }
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
