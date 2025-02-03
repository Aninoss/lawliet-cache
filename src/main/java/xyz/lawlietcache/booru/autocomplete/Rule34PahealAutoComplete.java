package xyz.lawlietcache.booru.autocomplete;

import xyz.lawlietcache.booru.BooruChoice;
import xyz.lawlietcache.core.WebCache;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Rule34PahealAutoComplete implements BooruAutoComplete {

    private final static Logger LOGGER = LoggerFactory.getLogger(Rule34PahealAutoComplete.class);

    @Override
    public List<BooruChoice> retrieve(WebCache webCache, String search) {
        if (search.isBlank()) {
            return Collections.emptyList();
        }

        String url = "https://rule34.paheal.net/api/internal/autocomplete?s=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
        String data = webCache.get(url, (int) Duration.ofHours(24).toMinutes()).getBody();
        try {
            return extractJson(new JSONObject(data)).stream()
                    .sorted((o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()))
                    .map(entry -> new BooruChoice()
                            .setName(entry.getKey() + " (" + entry.getValue() + ")")
                            .setValue(entry.getKey()))
                    .collect(Collectors.toList());
        } catch (JSONException e) {
            LOGGER.error("Rule34 Paheal counter invalid response: {}", data, e);
            return Collections.emptyList();
        }
    }

    private List<Pair<String, Integer>> extractJson(JSONObject jsonObject) {
        List<Pair<String, Integer>> tags = new ArrayList<>();
        for (String key : jsonObject.keySet()) {
            JSONObject json = jsonObject.getJSONObject(key);
            String tagKey = key;
            if (json.has("newtag") && !json.isNull("newtag")) {
                tagKey = json.getString("newtag");
            }

            tags.add(Pair.of(tagKey, json.getInt("count")));
        }
        return tags;
    }

}
