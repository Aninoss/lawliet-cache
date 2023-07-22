package pixiv;

import com.github.hanshsieh.pixivj.exception.AuthException;
import com.github.hanshsieh.pixivj.model.*;
import com.github.hanshsieh.pixivj.oauth.PixivOAuthClient;
import com.github.hanshsieh.pixivj.token.ThreadedTokenRefresher;
import core.WebCache;
import redis.clients.jedis.JedisPool;
import util.NSFWUtil;
import util.StringUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

        int tries = 5;
        PixivImage pixivImage;
        do {
            pixivImage = retrieveImageRaw(guildId, word, filters, strictFilters, 0);
        } while (--tries > 0 && !nsfwAllowed && (pixivImage != null && pixivImage.isNsfw()));

        return pixivImage;
    }

    public List<PixivImage> retrieveImagesBulk(String word, List<String> filters, List<String> strictFilters) throws PixivException {
        List<CustomIllustration> illusts = retrieveIllusts(word, 0, filters, strictFilters);
        if (illusts.isEmpty()) {
            return null;
        }

        return illusts.stream()
                .map(this::extractImage)
                .collect(Collectors.toList());
    }

    private PixivImage retrieveImageRaw(long guildId, String word, List<String> filters, List<String> strictFilters, int page) throws PixivException {
        List<CustomIllustration> illusts = retrieveIllusts(word, page, filters, strictFilters);
        PixivCache pixivCache = new PixivCache(jedisPool, guildId, word);
        if (illusts.isEmpty()) {
            if (page > 0) {
                pixivCache.removeFirst();
                return retrieveImageRaw(guildId, word, filters, strictFilters, 0);
            } else {
                return null;
            }
        }

        List<CustomIllustration> filteredIllusts = pixivCache.filter(illusts);
        if (filteredIllusts.isEmpty()) {
            if (page < MAX_PAGE) {
                return retrieveImageRaw(guildId, word, filters, strictFilters, page + 1);
            } else {
                pixivCache.removeFirst();
                return retrieveImageRaw(guildId, word, filters, strictFilters, 0);
            }
        } else {
            PixivImage pixivImage = extractImage(filteredIllusts.get(0));
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

    private List<CustomIllustration> retrieveIllusts(String word, int page, List<String> filters, List<String> strictFilters) throws PixivException {
        apiLogin();

        SearchedIllustsFilter searchedIllustsFilter = new SearchedIllustsFilter();
        searchedIllustsFilter.setWord(word);
        searchedIllustsFilter.setIncludeTranslatedTagResults(true);
        searchedIllustsFilter.setMergePlainKeywordResults(true);
        searchedIllustsFilter.setOffset(page * 30);

        //TODO: skip reported images
        try {
            return customPixivApiClient.searchIllusts(searchedIllustsFilter).getIllusts().stream()
                    .filter(illustration -> NSFWUtil.tagListAllowed(extractTags(illustration), filters, strictFilters) &&
                            illustration.getType() == IllustType.ILLUST)
                    .collect(Collectors.toList());
        } catch (com.github.hanshsieh.pixivj.exception.PixivException | IOException e) {
            throw new PixivException("Pixiv retrieval exception", e);
        }
    }

    private PixivImage extractImage(CustomIllustration illustration) {
        String imageUrl = illustration.getMetaSinglePage().getOriginalImageUrl() != null
                ? illustration.getMetaSinglePage().getOriginalImageUrl()
                : illustration.getMetaPages().get(0).getImageUrls().getOriginal();

        return new PixivImage()
                .setId(String.valueOf(illustration.getId()))
                .setTitle(illustration.getTitle())
                .setDescription(illustration.getCaption())
                .setAuthor(illustration.getUser().getName())
                .setAuthorUrl("https://www.pixiv.net/en/users/" + illustration.getUser().getId())
                .setUrl("https://www.pixiv.net/en/artworks/" + illustration.getId())
                .setImage(imageUrl)
                .setViews(illustration.getTotalView())
                .setBookmarks(illustration.getTotalBookmarks())
                .setNsfw(illustration.getTags().stream().anyMatch(tag -> tag.getName().equals("R-18")))
                .setInstant(illustration.getCreateDate().toInstant());
    }

    private List<String> extractTags(CustomIllustration illustration) {
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
