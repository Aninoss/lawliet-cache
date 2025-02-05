package xyz.lawlietcache.booru.autocomplete;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import xyz.lawlietcache.booru.BooruChoice;
import xyz.lawlietcache.core.WebCache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GelbooruAutoComplete implements BooruAutoComplete {

    @Override
    public List<BooruChoice> retrieve(WebCache webCache, String search) {
        if (search.isBlank()) {
            return Collections.emptyList();
        }

        ArrayList<BooruChoice> tags = new ArrayList<>();
        String url = "https://gelbooru.com/index.php?page=autocomplete2&term=" + URLEncoder.encode(search, StandardCharsets.UTF_8) + "&type=tag_query&limit=10";
        String data = webCache.get(url, (int) Duration.ofHours(24).toMinutes()).getBody();
        if (data == null) {
            return Collections.emptyList();
        }

        JSONArray arrayJson = new JSONArray(data);
        for (int i = 0; i < arrayJson.length(); i++) {
            JSONObject tagJson = arrayJson.getJSONObject(i);
            String name = StringEscapeUtils.unescapeHtml4(tagJson.getString("value"));
            int postCount = tagJson.getInt("post_count");
            BooruChoice choice = new BooruChoice()
                    .setName(name + " (" + postCount + ")")
                    .setValue(name);
            tags.add(choice);
        }

        return Collections.unmodifiableList(tags);
    }

}
