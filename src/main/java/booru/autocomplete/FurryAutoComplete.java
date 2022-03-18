package booru.autocomplete;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import booru.BooruChoice;
import core.WebCache;
import org.json.JSONArray;
import org.json.JSONObject;

public class FurryAutoComplete implements BooruAutoComplete {

    private final String domain;

    public FurryAutoComplete(String domain) {
        this.domain = domain;
    }

    @Override
    public List<BooruChoice> retrieve(WebCache webCache, String search) {
        if (search.length() < 3) {
            return Collections.emptyList();
        }

        ArrayList<BooruChoice> tags = new ArrayList<>();
        String url = "https://" + domain + "/tags/autocomplete.json?search%5Bname_matches%5D=" + URLEncoder.encode(search, StandardCharsets.UTF_8) + "&expiry=7";
        String data = webCache.get(url, 60).getBody();
        JSONArray arrayJson = new JSONArray(data);
        for (int i = 0; i < arrayJson.length(); i++) {
            JSONObject tagJson = arrayJson.getJSONObject(i);
            String name = tagJson.getString("name");
            int postCount = tagJson.getInt("post_count");
            BooruChoice choice = new BooruChoice()
                    .setName(name + " (" + postCount + ")")
                    .setValue(name);
            tags.add(choice);
        }

        return Collections.unmodifiableList(tags);
    }

}
