package xyz.lawlietcache.booru.autocomplete;

import java.util.Collections;
import java.util.List;
import xyz.lawlietcache.booru.BooruChoice;
import xyz.lawlietcache.core.WebCache;

public class EmptyAutoComplete implements BooruAutoComplete {

    @Override
    public List<BooruChoice> retrieve(WebCache webCache, String search) {
        return Collections.emptyList();
    }

}
