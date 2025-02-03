package xyz.lawlietcache.pixiv;

import com.github.hanshsieh.pixivj.exception.AuthException;
import com.github.hanshsieh.pixivj.model.*;
import com.github.hanshsieh.pixivj.oauth.PixivOAuthClient;
import com.github.hanshsieh.pixivj.token.ThreadedTokenRefresher;
import xyz.lawlietcache.core.HttpHeader;
import xyz.lawlietcache.core.JedisPoolManager;
import xyz.lawlietcache.core.WebCache;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import xyz.lawlietcache.pixiv.exception.PixivException;
import xyz.lawlietcache.util.NSFWUtil;
import xyz.lawlietcache.util.StringUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static xyz.lawlietcache.booru.BooruDownloader.REPORTS_KEY;

@Service
public class PixivDownloader {

    private final static int MAX_PAGE = 5;
    private final static Pattern AI_FILTER_PATTERN = Pattern.compile("(?i)(^|.* )-ai($| .*)");

    private final WebCache webCache;
    private final JedisPool jedisPool;
    private CustomPixivApiClient customPixivApiClient = null;

    public PixivDownloader(WebCache webCache, JedisPoolManager jedisPoolManager) {
        this.jedisPool = jedisPoolManager.get();
        this.webCache = webCache;
    }

    public List<PixivChoice> getTags(String search) {
        HttpHeader refererHeader = new HttpHeader("Referer", "https://www.pixiv.net/en/");
        ArrayList<PixivChoice> tags = new ArrayList<>();

        boolean negativeTag = search.startsWith("-");
        if (negativeTag) {
            search = search.substring(1);
        }

        if (search.equals("+")) {
            String url = "https://www.pixiv.net/ajax/search/suggestion?mode=all&lang=en&version=4cc435e5e04b2ed7bcf8f63d7282b497cc6e700b";
            String data = webCache.get(url, (int) Duration.ofHours(24).toMinutes(), refererHeader).getBody();

            JSONObject rootJson = new JSONObject(data).getJSONObject("body");
            JSONArray tagsJson = rootJson.getJSONObject("popularTags").getJSONArray("illust");
            JSONObject translationsJson = rootJson.getJSONObject("tagTranslation");

            for (int i = 0; i < Math.min(10, tagsJson.length()); i++) {
                String tag = tagsJson.getJSONObject(i).getString("tag");
                String translatedTag = null;
                if (translationsJson.has(tag) &&
                        translationsJson.getJSONObject(tag).has("en") &&
                        !translationsJson.getJSONObject(tag).getString("en").isBlank()
                ) {
                    translatedTag = translationsJson.getJSONObject(tag).getString("en");
                }

                PixivChoice choice = new PixivChoice()
                        .setTag(tag)
                        .setTranslatedTag(translatedTag);
                tags.add(choice);
            }
        } else {
            String url = "https://www.pixiv.net/rpc/cps.php?keyword=" + URLEncoder.encode(search, StandardCharsets.UTF_8) + "&lang=en&version=4cc435e5e04b2ed7bcf8f63d7282b497cc6e700b";
            String data = webCache.get(url, (int) Duration.ofHours(24).toMinutes(), refererHeader).getBody();

            JSONArray candidatesJson = new JSONObject(data).getJSONArray("candidates");
            for (int i = 0; i < Math.min(10, candidatesJson.length()); i++) {
                JSONObject candidateJson = candidatesJson.getJSONObject(i);
                String tag = candidateJson.getString("tag_name");
                String translatedTag = candidateJson.has("tag_translation") ? candidateJson.getString("tag_translation") : null;

                PixivChoice choice = new PixivChoice()
                        .setTag(tag)
                        .setTranslatedTag(translatedTag);
                tags.add(choice);
            }
        }

        return tags.stream()
                .map(choice -> {
                    if (negativeTag) {
                        return new PixivChoice()
                                .setTag("-" + choice.getTag())
                                .setTranslatedTag(choice.getTranslatedTag() != null ? choice.getTranslatedTag() : null);
                    } else {
                        return choice;
                    }
                })
                .collect(Collectors.toList());
    }

    public PixivImage retrieveImage(long guildId, String word, boolean nsfwAllowed, List<String> filters,
                                    List<String> strictFilters) throws PixivException {
        if (!nsfwAllowed) {
            word += " -R-18 -R18";
        }

        Set<String> blockSet;
        try (Jedis jedis = jedisPool.getResource()) {
            blockSet = jedis.hgetAll(REPORTS_KEY).keySet();
        }

        int tries = 5;
        PixivImage pixivImage;
        do {
            pixivImage = retrieveImageRaw(guildId, word + " users入り", filters, strictFilters, blockSet, 0, true);
            if (pixivImage == null) {
                pixivImage = retrieveImageRaw(guildId, word, filters, strictFilters, blockSet, 0, false);
            }
        } while (--tries > 0 && !nsfwAllowed && (pixivImage != null && pixivImage.isNsfw()));

        return pixivImage;
    }

    public List<PixivImage> retrieveImagesBulk(String word, List<String> filters, List<String> strictFilters) throws PixivException {
        List<Illustration> illustrations = retrieveIllustrations(word, 0);
        if (illustrations.isEmpty()) {
            return null;
        }

        Set<String> blockSet;
        try (Jedis jedis = jedisPool.getResource()) {
            blockSet = jedis.hgetAll(REPORTS_KEY).keySet();
        }

        return illustrations.stream()
                .filter(illust -> filterIllustration(illust, word, filters, strictFilters, blockSet))
                .map(this::extractPixivImage)
                .collect(Collectors.toList());
    }

    private PixivImage retrieveImageRaw(long guildId, String word, List<String> filters, List<String> strictFilters,
                                        Set<String> blockSet, int page, boolean popular
    ) throws PixivException {
        List<Illustration> illustrations = retrieveIllustrations(word, page);
        PixivCache pixivCache = new PixivCache(jedisPool, guildId, word);
        if (illustrations.isEmpty()) {
            if (page > 0 && !popular) {
                pixivCache.removeFirst();
                return retrieveImageRaw(guildId, word, filters, strictFilters, blockSet, 0, popular);
            } else {
                return null;
            }
        }

        List<Illustration> filteredIllustrations = pixivCache.filter(illustrations).stream()
                .filter(illust -> filterIllustration(illust, word, filters, strictFilters, blockSet))
                .collect(Collectors.toList());

        if (filteredIllustrations.isEmpty()) {
            if (page < MAX_PAGE) {
                return retrieveImageRaw(guildId, word, filters, strictFilters, blockSet, page + 1, popular);
            } else {
                if (popular) {
                    return null;
                } else {
                    pixivCache.removeFirst();
                    return retrieveImageRaw(guildId, word, filters, strictFilters, blockSet, 0, popular);
                }
            }
        } else {
            PixivImage pixivImage = extractPixivImage(filteredIllustrations.get(0));
            pixivCache.add(pixivImage.getId());
            return pixivImage;
        }
    }

    private void apiLogin() {
        if (customPixivApiClient != null) {
            return;
        }

        Credential credential = new Credential();
        credential.setRefreshToken(System.getenv("PIXIV_REFRESH_TOKEN"));

        PixivOAuthClient pixivOAuthClient = new PixivOAuthClient.Builder()
                .setUserAgent(WebCache.USER_AGENT)
                .build();
        AuthResult authenticate;
        try {
            authenticate = pixivOAuthClient.authenticate(credential);
        } catch (AuthException | IOException e) {
            throw new RuntimeException(e);
        }

        ThreadedTokenRefresher tokenRefresher = new ThreadedTokenRefresher.Builder()
                .setAuthClient(pixivOAuthClient)
                .build();
        tokenRefresher.updateTokens(authenticate.getAccessToken(), authenticate.getRefreshToken(), Instant.now().plusSeconds(authenticate.getExpiresIn()));

        this.customPixivApiClient = new CustomPixivApiClient(tokenRefresher, webCache);
    }

    private List<Illustration> retrieveIllustrations(String word, int page) throws PixivException {
        apiLogin();

        SearchedIllustsFilter searchedIllustsFilter = new SearchedIllustsFilter();
        searchedIllustsFilter.setWord(word + " -ロリ -ショタ -R-18G -R18G");
        searchedIllustsFilter.setIncludeTranslatedTagResults(true);
        searchedIllustsFilter.setMergePlainKeywordResults(true);
        searchedIllustsFilter.setOffset(page * 30);

        try {
            return customPixivApiClient.searchIllusts(searchedIllustsFilter).getIllusts();
        } catch (com.github.hanshsieh.pixivj.exception.PixivException | IOException e) {
            throw new PixivException("Pixiv retrieval exception", e);
        }
    }

    private boolean filterIllustration(Illustration illustration, String word, List<String> filters,
                                       List<String> strictFilters, Set<String> blockSet
    ) {
        boolean allowAi = !AI_FILTER_PATTERN.matcher(word).matches() &&
                filters.stream().noneMatch(filter -> filter.equalsIgnoreCase("ai"));

        return illustration.getType() != IllustType.UGOIRA &&
                (illustration.getIllustAiType() == 1 || allowAi) &&
                extractImageProxyUrls(illustration).stream().noneMatch(blockSet::contains) &&
                !NSFWUtil.containsFilterTags(extractTags(illustration), filters, strictFilters);
    }

    private PixivImage extractPixivImage(Illustration illustration) {
        return new PixivImage()
                .setId(String.valueOf(illustration.getId()))
                .setTitle(illustration.getTitle())
                .setDescription(illustration.getCaption())
                .setAuthor(illustration.getUser().getName())
                .setAuthorUrl("https://www.pixiv.net/en/users/" + illustration.getUser().getId())
                .setUrl("https://www.pixiv.net/en/artworks/" + illustration.getId())
                .setImageUrls(extractImageUrls(illustration))
                .setViews(illustration.getTotalView())
                .setBookmarks(illustration.getTotalBookmarks())
                .setNsfw(illustration.getSanityLevel() >= 6 || hasNsfwTags(illustration))
                .setInstant(illustration.getCreateDate().toInstant());
    }

    private List<String> extractImageUrls(Illustration illustration) {
        if (illustration.getMetaSinglePage().getOriginalImageUrl() != null) {
            return List.of(illustration.getMetaSinglePage().getOriginalImageUrl());
        } else {
            return illustration.getMetaPages().stream()
                    .map(page -> page.getImageUrls().getOriginal())
                    .collect(Collectors.toList());
        }
    }

    private List<String> extractImageProxyUrls(Illustration illustration) {
        List<String> imageUrls = extractImageUrls(illustration);
        List<String> imageProxyUrls = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String imageUrl = imageUrls.get(i);
            String extension = imageUrl.substring(imageUrl.lastIndexOf("."));
            imageProxyUrls.add("https://media-cdn.lawlietbot.xyz/pixiv/" + illustration.getId() + "_" + i + extension);
        }

        return imageProxyUrls;
    }

    private boolean hasNsfwTags(Illustration illustration) {
        List<String> tags = extractTags(illustration);
        return tags.stream().anyMatch(tag -> tag.equalsIgnoreCase("R-18") || tag.equalsIgnoreCase("R18"));
    }

    private List<String> extractTags(Illustration illustration) {
        HashSet<String> tags = new HashSet<>();
        for (Tag tag : illustration.getTags()) {
            if (tag.getName() != null && !tag.getName().isBlank()) {
                tags.add(tag.getName().toLowerCase());
                tags.add(StringUtil.camelToSnake(tag.getName()));
            }
            if (tag.getTranslatedName() != null && !tag.getTranslatedName().isBlank()) {
                tags.add(tag.getTranslatedName().toLowerCase());
                tags.add(StringUtil.camelToSnake(tag.getTranslatedName()));
            }
        }
        return new ArrayList<>(tags);
    }

}
