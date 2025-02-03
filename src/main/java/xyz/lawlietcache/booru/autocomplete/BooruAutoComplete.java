package xyz.lawlietcache.booru.autocomplete;

import java.util.List;
import xyz.lawlietcache.booru.BooruChoice;
import xyz.lawlietcache.core.WebCache;

public interface BooruAutoComplete {

    List<BooruChoice> retrieve(WebCache webCache, String search);

}
