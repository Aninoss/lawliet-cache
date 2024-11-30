package booru.workaroundsearcher;

import booru.BooruException;
import booru.BooruImage;
import booru.customboards.CustomImage;
import core.HttpResponse;
import core.WebCache;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;
import util.InternetUtil;
import util.StringUtil;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RealbooruWorkaroundSearcher implements WorkaroundSearcher {

    @Override
    public List<? extends BoardImage> search(int page, String searchTerm, WebCache webCache) throws BooruException {
        HttpResponse httpResponse = webCache.get("https://realbooru.com/index.php?page=post&s=list&tags=" + InternetUtil.escapeForURL(searchTerm), 30);
        if (httpResponse.getCode() / 100 != 2) {
            throw new BooruException();
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

        String uri = StringUtil.extractGroups(httpResponse.getBody(), "https://realbooru.com//images/", "\"")[0];
        booruImage.setImageUrl("https://realbooru.com//images/" + uri);
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
