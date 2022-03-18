package booru.autocomplete;

import java.util.Collections;
import java.util.List;
import booru.BooruChoice;
import core.WebCache;

public class EmptyAutoComplete implements BooruAutoComplete {

    @Override
    public List<BooruChoice> retrieve(WebCache webCache, String search) {
        return Collections.emptyList();
    }

}
