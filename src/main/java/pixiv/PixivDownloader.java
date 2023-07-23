package pixiv;

import com.github.hanshsieh.pixivj.exception.AuthException;
import com.github.hanshsieh.pixivj.model.*;
import com.github.hanshsieh.pixivj.oauth.PixivOAuthClient;
import com.github.hanshsieh.pixivj.token.ThreadedTokenRefresher;
import core.WebCache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import util.NSFWUtil;
import util.StringUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static booru.BooruDownloader.REPORTS_KEY;

public class PixivDownloader {

    private final static int MAX_PAGE = 5;

    private final WebCache webCache;
    private final JedisPool jedisPool;
    private CustomPixivApiClient customPixivApiClient = null;

    public PixivDownloader(WebCache webCache, JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.webCache = webCache;
    }

    public PixivImage retrieveImage(long guildId, String word, boolean nsfwAllowed, List<String> filters,
                                    List<String> strictFilters) throws PixivException {
        if (!nsfwAllowed) {
            word = word + " -r-18";
        }

        Set<String> blockSet;
        try (Jedis jedis = jedisPool.getResource()) {
            blockSet = jedis.hgetAll(REPORTS_KEY).keySet();
        }

        int tries = 5;
        PixivImage pixivImage;
        do {
            pixivImage = retrieveImageRaw(guildId, word, filters, strictFilters, blockSet, 0);
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
                .filter(illust -> filterIllustration(illust, filters, strictFilters, blockSet))
                .map(this::extractPixivImage)
                .collect(Collectors.toList());
    }

    private PixivImage retrieveImageRaw(long guildId, String word, List<String> filters, List<String> strictFilters,
                                        Set<String> blockSet, int page
    ) throws PixivException {
        List<Illustration> illustrations = retrieveIllustrations(word, page);
        PixivCache pixivCache = new PixivCache(jedisPool, guildId, word);
        if (illustrations.isEmpty()) {
            if (page > 0) {
                pixivCache.removeFirst();
                return retrieveImageRaw(guildId, word, filters, strictFilters, blockSet, 0);
            } else {
                return null;
            }
        }

        List<Illustration> filteredIllustrations = pixivCache.filter(illustrations).stream()
                .filter(illust -> filterIllustration(illust, filters, strictFilters, blockSet))
                .collect(Collectors.toList());

        if (filteredIllustrations.isEmpty()) {
            if (page < MAX_PAGE) {
                return retrieveImageRaw(guildId, word, filters, strictFilters, blockSet, page + 1);
            } else {
                pixivCache.removeFirst();
                return retrieveImageRaw(guildId, word, filters, strictFilters, blockSet, 0);
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
        searchedIllustsFilter.setWord(word);
        searchedIllustsFilter.setIncludeTranslatedTagResults(true);
        searchedIllustsFilter.setMergePlainKeywordResults(true);
        searchedIllustsFilter.setOffset(page * 30);

        try {
            return customPixivApiClient.searchIllusts(searchedIllustsFilter).getIllusts();
        } catch (com.github.hanshsieh.pixivj.exception.PixivException | IOException e) {
            throw new PixivException("Pixiv retrieval exception", e);
        }
    }

    private boolean filterIllustration(Illustration illustration, List<String> filters, List<String> strictFilters,
                                       Set<String> blockSet
    ) {
        return NSFWUtil.tagListAllowed(extractTags(illustration), filters, strictFilters) &&
                illustration.getType() == IllustType.ILLUST &&
                !blockSet.contains(extractImageProxyUrl(illustration));
    }

    private PixivImage extractPixivImage(Illustration illustration) {
        return new PixivImage()
                .setId(String.valueOf(illustration.getId()))
                .setTitle(illustration.getTitle())
                .setDescription(illustration.getCaption())
                .setAuthor(illustration.getUser().getName())
                .setAuthorUrl("https://www.pixiv.net/en/users/" + illustration.getUser().getId())
                .setUrl("https://www.pixiv.net/en/artworks/" + illustration.getId())
                .setImageUrl(extractImageUrl(illustration))
                .setViews(illustration.getTotalView())
                .setBookmarks(illustration.getTotalBookmarks())
                .setNsfw(illustration.getTags().stream().anyMatch(tag -> tag.getName().equals("R-18")))
                .setInstant(illustration.getCreateDate().toInstant());
    }

    private String extractImageUrl(Illustration illustration) {
        return illustration.getMetaSinglePage().getOriginalImageUrl() != null
                ? illustration.getMetaSinglePage().getOriginalImageUrl()
                : illustration.getMetaPages().get(0).getImageUrls().getOriginal();
    }

    private String extractImageProxyUrl(Illustration illustration) {
        String imageUrl = extractImageUrl(illustration);
        String extension = imageUrl.substring(imageUrl.lastIndexOf("."));
        return "https://lawlietbot.xyz/cdn/pixiv/" + illustration.getId() + extension;
    }

    private List<String> extractTags(Illustration illustration) {
        List<String> tags = new ArrayList<>();
        for (Tag tag : illustration.getTags()) {
            if (tag.getName() != null) {
                tags.add(StringUtil.camelToSnake(tag.getName()));
            }
            if (tag.getTranslatedName() != null) {
                tags.add(StringUtil.camelToSnake(tag.getTranslatedName()));
            }
        }
        return tags;
    }

}
