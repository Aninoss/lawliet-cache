package xyz.lawlietcache.booru.workaroundsearcher;

import xyz.lawlietcache.booru.exception.BooruException;
import xyz.lawlietcache.booru.BooruImage;
import xyz.lawlietcache.core.WebCache;
import net.kodehawa.lib.imageboards.entities.BoardImage;

import java.util.List;

public interface WorkaroundSearcher {

    List<? extends BoardImage> search(int page, String searchTerm, WebCache webCache) throws BooruException;

    void postProcess(WebCache webCache, BooruImage booruImage);

}