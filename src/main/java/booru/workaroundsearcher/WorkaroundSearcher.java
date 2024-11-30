package booru.workaroundsearcher;

import booru.BooruException;
import booru.BooruImage;
import core.WebCache;
import net.kodehawa.lib.imageboards.entities.BoardImage;

import java.util.List;

public interface WorkaroundSearcher {

    List<? extends BoardImage> search(int page, String searchTerm, WebCache webCache) throws BooruException;

    void postProcess(WebCache webCache, BooruImage booruImage);

}