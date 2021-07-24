package booru.counters;

import core.WebCache;

public interface Counter {

    int count(WebCache webCache, String tags);

}
