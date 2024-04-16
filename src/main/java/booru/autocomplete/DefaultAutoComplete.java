package booru.autocomplete;

import booru.BooruChoice;
import core.WebCache;
import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultAutoComplete implements BooruAutoComplete {

    private final static Logger LOGGER = LoggerFactory.getLogger(DefaultAutoComplete.class);

    private final String domain;

    public DefaultAutoComplete(String domain) {
        this.domain = domain;
    }

    @Override
    public List<BooruChoice> retrieve(WebCache webCache, String search) {
        ArrayList<BooruChoice> tags = new ArrayList<>();
        String url = "https://" + domain + "/autocomplete.php?q=" + URLEncoder.encode(search, StandardCharsets.UTF_8);
        String data = webCache.get(url, (int) Duration.ofHours(24).toMinutes()).getBody();

        JSONArray arrayJson;
        try {
            arrayJson = new JSONArray(data);
        } catch (Throwable e) {
            LOGGER.error("Invalid auto complete response for {}: {}", domain, data, e);
            return Collections.emptyList();
        }

        for (int i = 0; i < arrayJson.length(); i++) {
            JSONObject tagJson = arrayJson.getJSONObject(i);
            BooruChoice choice = new BooruChoice()
                    .setName(StringEscapeUtils.unescapeHtml4(tagJson.getString("label")))
                    .setValue(StringEscapeUtils.unescapeHtml4(tagJson.getString("value")));
            tags.add(choice);
        }

        return Collections.unmodifiableList(tags);
    }

}
