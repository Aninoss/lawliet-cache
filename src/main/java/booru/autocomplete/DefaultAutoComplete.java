package booru.autocomplete;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import booru.BooruChoice;
import core.WebCache;
import org.json.JSONArray;
import org.json.JSONObject;

public class DefaultAutoComplete implements BooruAutoComplete {

    private final String domain;

    public DefaultAutoComplete(String domain) {
        this.domain = domain;
    }

    @Override
    public List<BooruChoice> retrieve(WebCache webCache, String search) {
        ArrayList<BooruChoice> tags = new ArrayList<>();
        String url = "https://" + domain + "/autocomplete.php?q=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
        String data = webCache.get(url, (int) Duration.ofHours(24).toMinutes()).getBody();
        JSONArray arrayJson = new JSONArray(data);
        for (int i = 0; i < arrayJson.length(); i++) {
            JSONObject tagJson = arrayJson.getJSONObject(i);
            BooruChoice choice = new BooruChoice()
                    .setName(tagJson.getString("label"))
                    .setValue(tagJson.getString("value"));
            tags.add(choice);
        }

        return Collections.unmodifiableList(tags);
    }

}
