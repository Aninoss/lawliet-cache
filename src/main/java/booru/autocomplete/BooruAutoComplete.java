package booru.autocomplete;

import java.util.List;
import booru.BooruChoice;
import core.WebCache;

public interface BooruAutoComplete {

    List<BooruChoice> retrieve(WebCache webCache, String search);

}
