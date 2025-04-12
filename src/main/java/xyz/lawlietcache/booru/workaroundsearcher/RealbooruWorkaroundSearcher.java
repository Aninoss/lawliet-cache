package xyz.lawlietcache.booru.workaroundsearcher;

import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.lawlietcache.booru.BooruImage;
import xyz.lawlietcache.booru.customboards.CustomImage;
import xyz.lawlietcache.booru.exception.BooruException;
import xyz.lawlietcache.booru.exception.SilentBooruException;
import xyz.lawlietcache.core.HttpResponse;
import xyz.lawlietcache.core.WebCache;
import xyz.lawlietcache.util.InternetUtil;
import xyz.lawlietcache.util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RealbooruWorkaroundSearcher implements WorkaroundSearcher {

    private final static Logger LOGGER = LoggerFactory.getLogger(RealbooruWorkaroundSearcher.class);

    @Override
    public List<? extends BoardImage> search(int page, String searchTerm, WebCache webCache) throws BooruException {
        HttpResponse httpResponse = webCache.get("https://realbooru.com/index.php?page=post&s=list&pid=" + (page * 42) + "&tags=" + InternetUtil.escapeForURL(searchTerm), 30);
        if (httpResponse.getCode() / 100 != 2) {
            throw new SilentBooruException();
        }

        String[] posts = StringUtil.extractGroups(httpResponse.getBody(), "<div class=\"col thumb\"", "</a></div>");
        if (posts.length == 0) {
            return null;
        }

        return Stream.of(posts)
                .map(this::htmlToBoardImage)
                .collect(Collectors.toList());
    }

    @Override
    public void postProcess(WebCache webCache, BooruImage booruImage) {
        if (!booruImage.getImageUrl().endsWith(".jpeg")) {
            return;
        }

        HttpResponse httpResponse = webCache.get(booruImage.getPageUrl(), 30);
        if (httpResponse.getCode() / 100 != 2) {
            return;
        }

        try {
            String uri;
            if (httpResponse.getBody().contains("https://realbooru.com//images/")) {
                uri = StringUtil.extractGroups(httpResponse.getBody(), "https://realbooru.com//images/", "\"")[0].replace(".webm", ".mp4");
            } else  {
                uri = StringUtil.extractGroups(httpResponse.getBody(), "https://video-cdn.realbooru.com//images/", "\"")[0].replace(".webm", ".mp4");
            }
            booruImage.setImageUrl("https://realbooru.com//images/" + uri);
        } catch (Throwable e) {
            LOGGER.error("Realbooru workaround post process exception:\n{}", httpResponse.getBody(), e);
        }
    }

    private CustomImage htmlToBoardImage(String html) {
        List<String> tags = List.of(StringUtil.extractGroups(html, "title=\"", "\"")[0].split(", "));
        boolean isVideo = tags.contains("webm");
        String contentUrl = StringUtil.extractGroups(html, "<img src=\"", "\"")[0]
                .replace("/thumbnails", "//images")
                .replace("/thumbnail_", "/")
                .replace(".jpg", isVideo ? ".mp4" : ".jpeg");

        return new CustomImage(
                Long.parseLong(StringUtil.extractGroups(html, "id=\"", "\"")[0].substring(1)),
                0,
                0,
                0,
                Rating.EXPLICIT,
                tags,
                contentUrl,
                false,
                false,
                0
        );
    }

}
