package booru;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import util.InternetUtil;
import util.NSFWUtil;
import util.StringUtil;
import util.TimeUtil;

public class BooruDownloader {

    public static final int PAGE_LIMIT = 999;

    private final OkHttpClient client = new OkHttpClient();
    private final BooruFilter booruFilter = new BooruFilter();
    private final LoadingCache<String, String> httpCache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .build(new CacheLoader<>() {
                @Override
                public String load(@NonNull String url) throws Exception {
                    Request request = new Request.Builder()
                            .url(url)
                            .build();

                    return client.newCall(request).execute().body().string();
                }
            });

    public Optional<BooruImage> getPicture(long guildId, String domain, String searchTerm, String searchTermExtra,
                                           String imageTemplate, boolean animatedOnly, boolean canBeVideo,
                                           boolean explicit, List<String> filters, List<String> skippedResults
    ) throws ExecutionException {
        searchTerm = NSFWUtil.filterPornSearchKey(searchTerm, filters);

        return getPicture(guildId, domain, searchTerm, searchTermExtra, imageTemplate, animatedOnly, canBeVideo,
                explicit, 2, false, filters, skippedResults);
    }

    private Optional<BooruImage> getPicture(long guildId, String domain, String searchTerm, String searchTermExtra,
                                            String imageTemplate, boolean animatedOnly, boolean canBeVideo,
                                            boolean explicit, int remaining, boolean softMode,
                                            List<String> additionalFilters, List<String> skippedResults
    ) throws ExecutionException {
        while (searchTerm.contains("  ")) searchTerm = searchTerm.replace("  ", " ");
        searchTerm = searchTerm.replace(", ", ",");
        searchTerm = searchTerm.replace("; ", ",");

        String searchTermEncoded = InternetUtil.escapeForURL(
                searchTerm
                        .replace(",", " ")
                        .replace(" ", softMode ? "~ " : " ") +
                        (softMode ? "~" : "") +
                        searchTermExtra
        );

        String url = "https://" + domain + "/index.php?page=dapi&s=post&q=index&limit=" + PAGE_LIMIT + "&tags=" + searchTermEncoded;

        String data = httpCache.get(url);
        if (!data.contains("count=\"")) {
            return Optional.empty();
        }
        int count = Math.min(20_000 / PAGE_LIMIT * PAGE_LIMIT, Integer.parseInt(StringUtil.extractGroups(data, "count=\"", "\"")[0]));
        if (count == 0) {
            if (!softMode) {
                return getPicture(guildId, domain, searchTerm.replace(" ", "_"), searchTermExtra, imageTemplate,
                        animatedOnly, canBeVideo, explicit, remaining, true, additionalFilters, skippedResults);
            } else if (remaining > 0) {
                if (searchTerm.contains(" ")) {
                    return getPicture(guildId, domain, searchTerm.replace(" ", "_"), searchTermExtra, imageTemplate,
                            animatedOnly, canBeVideo, explicit, remaining - 1, false,
                            additionalFilters,skippedResults);
                } else if (searchTerm.contains("_")) {
                    return getPicture(guildId, domain, searchTerm.replace("_", " "), searchTermExtra, imageTemplate,
                            animatedOnly, canBeVideo, explicit, remaining - 1, false,
                            additionalFilters, skippedResults);
                }
            }

            return Optional.empty();
        }

        Random r = new Random();
        int page = r.nextInt(count) / PAGE_LIMIT;
        if (searchTermEncoded.length() == 0)  {
            page = 0;
        }

        return getPictureOnPage(guildId, domain, searchTermEncoded, page, imageTemplate, animatedOnly, canBeVideo,
                explicit, additionalFilters, skippedResults, count - 1);
    }

    private Optional<BooruImage> getPictureOnPage(long guildId, String domain, String searchTerm, int page,
                                                        String imageTemplate, boolean animatedOnly, boolean canBeVideo,
                                                        boolean explicit, List<String> filters,
                                                        List<String> skippedResults, int maxSize
    ) throws ExecutionException {
        String url = "https://" + domain + "/index.php?page=dapi&s=post&q=index&limit=" + PAGE_LIMIT + "&json=1&tags=" + searchTerm + "&pid=" + page;
        String content = httpCache.get(url);

        JSONArray data = new JSONArray(content);

        int count = Math.min(data.length(), PAGE_LIMIT);
        if (count == 0) {
            return Optional.empty();
        }

        ArrayList<BooruImageMeta> pornImages = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            JSONObject postData = data.getJSONObject(i);

            String imageUrl = postData.getString(postData.has("file_url") ? "file_url" : "image");

            long score = 1;
            boolean postIsImage = InternetUtil.urlContainsImage(imageUrl);
            boolean postIsGif = imageUrl.endsWith("gif");

            if (postData.has("score") && !postData.isNull("score")) {
                try {
                    score = postData.getInt("score");
                } catch (JSONException e) {
                    //Ignore
                }
            }

            boolean isExplicit = postData.getString("rating").startsWith("e");

            if ((postIsImage || canBeVideo) &&
                    (!animatedOnly || postIsGif || !postIsImage) &&
                    score >= 0 &&
                    !NSFWUtil.stringContainsBannedTags(postData.getString("tags"), filters) &&
                    isExplicit == explicit
            ) {
                pornImages.add(new BooruImageMeta(imageUrl, score, i));
            }
        }

        return Optional.ofNullable(booruFilter.filter(guildId, domain, searchTerm, pornImages, skippedResults, maxSize))
                .map(pornImageMeta -> getSpecificPictureOnPage(domain, data.getJSONObject(pornImageMeta.getIndex()), imageTemplate));
    }

    private BooruImage getSpecificPictureOnPage(String domain, JSONObject postData, String imageTemplate) {
        String postURL = "https://" + domain + "/index.php?page=post&s=view&id=" + postData.getInt("id");

        Instant instant;
        if (postData.has("created_at")) {
            instant = TimeUtil.parseDateString(postData.getString("created_at"));
        } else {
            instant = Instant.now();
        }

        String fileURL;
        if (postData.has("file_url")) {
            fileURL = postData.getString("file_url");
        } else {
            fileURL = imageTemplate.replace("%d", postData.get("directory").toString())
                    .replace("%f", postData.getString("image"));
        }

        if (fileURL.contains("?")) {
            fileURL = fileURL.split("\\?")[0];
        }

        int score = 0;
        try {
            score = postData.getInt("score");
        } catch (JSONException e) {
            //Ignore
        }

        return new BooruImage()
                .setImageUrl(fileURL)
                .setPageUrl(postURL)
                .setScore(score)
                .setInstant(instant)
                .setVideo(!InternetUtil.urlContainsImage(fileURL) && !fileURL.endsWith("gif"));
    }

}
