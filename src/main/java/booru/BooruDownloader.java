package booru;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import core.Program;
import core.WebCache;
import net.kodehawa.lib.imageboards.ImageBoard;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import util.NSFWUtil;

public class BooruDownloader {

    private final static Logger LOGGER = LoggerFactory.getLogger(BooruDownloader.class);

    private final BooruFilter booruFilter;
    private final OkHttpClient client;
    private final WebCache webCache;
    private final Random random = new Random();
    private final JedisPool jedisPool;

    public BooruDownloader(WebCache webCache, JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.booruFilter = new BooruFilter(jedisPool);
        this.client = webCache.getClient().newBuilder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    request = request.newBuilder()
                            .url(String.format("http://localhost:%s/api/cached_proxy/15", System.getenv("PORT")))
                            .method("POST", RequestBody.create(request.url().toString(), MediaType.get("text/plain")))
                            .addHeader("Authorization", System.getenv("AUTH"))
                            .build();
                    return chain.proceed(request);
                })
                .build();
        this.webCache = webCache;
        ImageBoard.setUserAgent(WebCache.USER_AGENT);
    }

    public Optional<BooruImage> getPicture(long guildId, String domain, String searchTerm, boolean animatedOnly,
                                           boolean explicit, List<String> filters, List<String> skippedResults,
                                           boolean test) {
        searchTerm = NSFWUtil.filterPornSearchKey(searchTerm, filters);
        BoardType boardType = BoardType.fromDomain(domain);
        if (boardType == null) {
            throw new NoSuchElementException("No such image board");
        }

        return getPicture(guildId, boardType, searchTerm, animatedOnly, explicit, 2, false, filters,
                skippedResults, test
        );
    }

    private Optional<BooruImage> getPicture(long guildId, BoardType boardType, String searchTerm, boolean animatedOnly,
                                            boolean explicit, int remaining, boolean softMode,
                                            List<String> filters, List<String> skippedResults, boolean test
    ) {
        while (searchTerm.contains("  ")) searchTerm = searchTerm.replace("  ", " ");
        searchTerm = searchTerm.replace(", ", ",")
                .replace("; ", ",")
                .replace("+", " ");
        StringBuilder finalSearchTerm = new StringBuilder(searchTerm
                .replace(",", " ")
                .replace(" ", softMode ? "~ " : " ") +
                (softMode ? "~" : ""));

        if (boardType.getMaxTags() < 0) {
            filters.forEach(filter -> finalSearchTerm.append(" -").append(filter));
        }

        String finalSearchTermString = finalSearchTerm.toString();
        if (boardType.getMaxTags() >= 0) {
            finalSearchTermString = reduceTags(finalSearchTermString, boardType.getMaxTags());
        }
        if (boardType == BoardType.DANBOORU) {
            finalSearchTermString += " -filetype:zip";
        }

        int count = Math.min(20_000 / boardType.getMaxLimit() * boardType.getMaxLimit(), boardType.count(webCache, finalSearchTermString));
        if (count == 0 && !test) {
            if (!softMode) {
                return getPicture(guildId, boardType, searchTerm.replace(" ", "_"), animatedOnly, explicit, remaining,
                        true, filters, skippedResults, test
                );
            } else if (remaining > 0) {
                if (searchTerm.contains(" ")) {
                    return getPicture(guildId, boardType, searchTerm.replace(" ", "_"), animatedOnly, explicit,
                            remaining - 1, false, filters, skippedResults, test
                    );
                } else if (searchTerm.contains("_")) {
                    return getPicture(guildId, boardType, searchTerm.replace("_", " "), animatedOnly, explicit,
                            remaining - 1, false, filters, skippedResults, test
                    );
                }
            }

            return Optional.empty();
        } else if (test) {
            return Optional.of(new BooruImage());
        }

        int shift = count >= 19_000 ? 2000 : 0;
        int page = (shift + random.nextInt(count - shift)) / boardType.getMaxLimit();

        return getPictureOnPage(guildId, boardType, finalSearchTermString, page, animatedOnly, explicit,
                filters, skippedResults
        );
    }

    private String reduceTags(String finalSearchTermString, int maxTags) {
        String[] tags = finalSearchTermString.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(tags.length, maxTags); i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(tags[i]);
        }
        return sb.toString();
    }

    private Optional<BooruImage> getPictureOnPage(long guildId, BoardType boardType, String searchTerm, int page,
                                                  boolean animatedOnly, boolean explicit, List<String> filters,
                                                  List<String> skippedResults
    ) {
        ImageBoard<? extends BoardImage> imageBoard = new ImageBoard<>(client, boardType.getBoard(), boardType.getBoardImageClass());
        List<? extends BoardImage> boardImages;
        try {
            boardImages = imageBoard.search(page, boardType.getMaxLimit(), searchTerm).blocking();
        } catch (Throwable e) {
            LOGGER.error("Error in imageboard type {}", boardType.getDomain(), e);
            return Optional.empty();
        }

        if (boardImages.isEmpty()) {
            return Optional.empty();
        }

        ArrayList<BooruImageMeta> pornImages = new ArrayList<>();
        long maxPostDate = System.currentTimeMillis() - Duration.ofDays(3).toMillis();
        Set<String> blockSet;
        try (Jedis jedis = jedisPool.getResource()) {
            blockSet = jedis.hgetAll("reports").keySet();
        }

        for (BoardImage boardImage : boardImages) {
            String fileUrl = boardImage.getURL();
            if (fileUrl != null) {
                int score = boardImage.getScore();
                ContentType contentType = ContentType.parseFromUrl(fileUrl);
                boolean isExplicit = boardImage.getRating() == Rating.EXPLICIT;
                boolean notPending = !boardImage.isPending();
                long created = boardImage.getCreationMillis();
                boolean blocked = blockSet.contains(fileUrl);

                if (contentType != null &&
                        (!animatedOnly || contentType.isAnimated()) &&
                        score >= 0 &&
                        NSFWUtil.tagListAllowed(boardImage.getTags(), filters) &&
                        isExplicit == explicit &&
                        notPending &&
                        created <= maxPostDate &&
                        !blocked
                ) {
                    pornImages.add(new BooruImageMeta(fileUrl, score, boardImage, contentType));
                }
            }
        }

        return Optional.ofNullable(booruFilter.filter(guildId, boardType.name(), searchTerm, pornImages, skippedResults, pornImages.size() - 1))
                .map(pornImageMeta -> createBooruImage(boardType, pornImageMeta.getBoardImage(), pornImageMeta.getContentType()));
    }

    private BooruImage createBooruImage(BoardType boardType, BoardImage image, ContentType contentType) {
        String imageUrl = image.getURL();
        String pageUrl = boardType.getPageUrl(image.getId());
        if (boardType == BoardType.RULE34 && contentType.isVideo() && (!Program.isProductionMode() || random.nextInt(10) == 0)) {
            imageUrl = translateVideoUrlToOwnCDN(imageUrl);
        }

        return new BooruImage()
                .setImageUrl(imageUrl)
                .setPageUrl(pageUrl)
                .setScore(image.getScore())
                .setInstant(Instant.ofEpochMilli(image.getCreationMillis()));
    }

    private String translateVideoUrlToOwnCDN(String videoUrl) {
        String[] parts = videoUrl.split("/");
        return "https://media-cdn.lawlietbot.xyz/media/rule34/" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

}
