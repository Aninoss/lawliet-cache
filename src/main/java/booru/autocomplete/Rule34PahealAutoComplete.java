package booru.autocomplete;

import booru.BooruChoice;
import core.WebCache;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Rule34PahealAutoComplete implements BooruAutoComplete {

    @Override
    public List<BooruChoice> retrieve(WebCache webCache, String search) {
        if (search.isBlank()) {
            return Collections.emptyList();
        }

        String url = "https://rule34.paheal.net/api/internal/autocomplete?s=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
        String data = webCache.get(url, (int) Duration.ofHours(24).toMinutes()).getBody();
        return extractJson(new JSONObject(data)).stream()
                .sorted((o1, o2) -> Integer.compare(o2.getValue(), o1.getValue()))
                .map(entry -> new BooruChoice()
                        .setName(entry.getKey() + " (" + entry.getValue() + ")")
                        .setValue(entry.getKey()))
                .collect(Collectors.toList());
    }

    private List<Pair<String, Integer>> extractJson(JSONObject jsonObject) {
        List<Pair<String, Integer>> tags = new ArrayList<>();
        for (String key : jsonObject.keySet()) {
            tags.add(Pair.of(key, jsonObject.getInt(key)));
        }
        return tags;
    }

}
