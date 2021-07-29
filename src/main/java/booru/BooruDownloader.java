package booru;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import core.WebCache;
import net.kodehawa.lib.imageboards.ImageBoard;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import util.InternetUtil;
import util.NSFWUtil;

public class BooruDownloader {

    private final static Logger LOGGER = LoggerFactory.getLogger(BooruDownloader.class);

    private final BooruFilter booruFilter;
    private final OkHttpClient client;
    private final WebCache webCache;
    private final Random random = new Random();
    private final ArrayList<Integer> activeVideoDownloads = new ArrayList<>();

    public BooruDownloader(WebCache webCache, JedisPool jedisPool) {
        this.booruFilter = new BooruFilter(jedisPool);
        this.client = webCache.getClient().newBuilder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    request = request.newBuilder()
                            .url(String.format("http://localhost:%s/api/cached_proxy/15", System.getenv("PORT")))
                            .method("POST", RequestBody.create(MediaType.get("text/plain"), request.url().toString()))
                            .addHeader("Authorization", System.getenv("AUTH"))
                            .build();
                    return chain.proceed(request);
                })
                .build();
        this.webCache = webCache;
        ImageBoard.setUserAgent(WebCache.USER_AGENT);
    }

    public Optional<BooruImage> getPicture(long guildId, String domain, String searchTerm, boolean animatedOnly,
                                           boolean explicit, List<String> filters, List<String> skippedResults) {
        searchTerm = NSFWUtil.filterPornSearchKey(searchTerm, filters);
        BoardType boardType = BoardType.fromDomain(domain);
        if (boardType == null) {
            throw new NoSuchElementException("No such image board");
        }

        return getPicture(guildId, boardType, searchTerm, animatedOnly,
                explicit, 2, false, filters, skippedResults
        );
    }

    private Optional<BooruImage> getPicture(long guildId, BoardType boardType, String searchTerm, boolean animatedOnly,
                                            boolean explicit, int remaining, boolean softMode,
                                            List<String> additionalFilters, List<String> skippedResults
    ) {
        while (searchTerm.contains("  ")) searchTerm = searchTerm.replace("  ", " ");
        searchTerm = searchTerm.replace(", ", ",");
        searchTerm = searchTerm.replace("; ", ",");
        StringBuilder finalSearchTerm = new StringBuilder(searchTerm
                .replace(",", " ")
                .replace(" ", softMode ? "~ " : " ") +
                (softMode ? "~" : ""));
        additionalFilters.forEach(filter -> finalSearchTerm.append(" -").append(filter));

        int count = Math.min(20_000 / boardType.getMaxLimit() * boardType.getMaxLimit(), boardType.count(webCache, finalSearchTerm.toString()));
        if (count == 0) {
            if (!softMode) {
                return getPicture(guildId, boardType, searchTerm.replace(" ", "_"), animatedOnly, explicit, remaining,
                        true, additionalFilters, skippedResults
                );
            } else if (remaining > 0) {
                if (searchTerm.contains(" ")) {
                    return getPicture(guildId, boardType, searchTerm.replace(" ", "_"), animatedOnly, explicit,
                            remaining - 1, false, additionalFilters, skippedResults
                    );
                } else if (searchTerm.contains("_")) {
                    return getPicture(guildId, boardType, searchTerm.replace("_", " "), animatedOnly, explicit,
                            remaining - 1, false, additionalFilters, skippedResults
                    );
                }
            }

            return Optional.empty();
        }

        int page = random.nextInt(count) / boardType.getMaxLimit();
        if (finalSearchTerm.length() == 0) {
            page = 0;
        }

        return getPictureOnPage(guildId, boardType, finalSearchTerm.toString(), page, animatedOnly, explicit,
                additionalFilters, skippedResults
        );
    }

    private Optional<BooruImage> getPictureOnPage(long guildId, BoardType boardType, String searchTerm, int page,
                                                  boolean animatedOnly, boolean explicit, List<String> filters,
                                                  List<String> skippedResults
    ) {
        ImageBoard<? extends BoardImage> imageBoard = new ImageBoard<>(client, boardType.getBoard(), boardType.getBoardImageClass());
        List<? extends BoardImage> boardImages = imageBoard.search(page, boardType.getMaxLimit(), searchTerm).blocking();

        if (boardImages.isEmpty()) {
            return Optional.empty();
        }

        ArrayList<BooruImageMeta> pornImages = new ArrayList<>();
        for (BoardImage boardImage : boardImages) {
            String fileUrl = boardImage.getURL();
            if (fileUrl != null) {
                int score = boardImage.getScore();
                boolean postIsImage = InternetUtil.urlContainsImage(fileUrl);
                boolean postIsGif = fileUrl.endsWith("gif");
                boolean isExplicit = boardImage.getRating() == Rating.EXPLICIT;
                boolean notPending = !boardImage.isPending();

                if ((!animatedOnly || postIsGif || !postIsImage) &&
                        score >= 0 &&
                        NSFWUtil.tagListAllowed(boardImage.getTags(), filters) &&
                        isExplicit == explicit &&
                        notPending &&
                        !fileUrl.endsWith("swf")
                ) {
                    pornImages.add(new BooruImageMeta(fileUrl, score, boardImage));
                }
            }
        }

        return Optional.ofNullable(booruFilter.filter(guildId, boardType.name(), searchTerm, pornImages, skippedResults, boardImages.size() - 1))
                .map(pornImageMeta -> createBooruImage(boardType, pornImageMeta.getBoardImage()));
    }

    private BooruImage createBooruImage(BoardType boardType, BoardImage image) {
        String imageUrl = image.getURL();
        String pageUrl = boardType.getPageUrl(image.getId());
        boolean video = InternetUtil.urlContainsVideo(imageUrl);
        if (video) {
            imageUrl = downloadVideo(boardType, image.getId(), imageUrl);
        }

        return new BooruImage()
                .setImageUrl(imageUrl)
                .setPageUrl(pageUrl)
                .setScore(image.getScore())
                .setInstant(Instant.ofEpochMilli(image.getCreationMillis()))
                .setVideo(video);
    }

    private String downloadVideo(BoardType boardType, int id, final String imageUrl) {
        String[] fileParts = imageUrl.split("\\.");
        String fileExt = fileParts[fileParts.length - 1];
        String dirPath = System.getenv("VIDEO_CDN_PATH") + "/" + boardType.getDomain();
        File dir = new File(dirPath);
        File videoFile = new File(dirPath + "/" + id + "." + fileExt);

        if (dir.exists() && dir.isDirectory() && dir.canWrite()) {
            if (!videoFile.exists()) {
                if (activeVideoDownloads.size() < 5) {
                    activeVideoDownloads.add(id);
                    LOGGER.info("Downloading video: {}", id);
                    try {
                        FileUtils.copyURLToFile(
                                new URL(imageUrl),
                                videoFile,
                                3_000,
                                20_000
                        );
                    } catch (IOException e) {
                        LOGGER.error("Exception on video download", e);
                    }
                    LOGGER.info("Video download complete: {}", id);
                    activeVideoDownloads.remove((Integer) id);
                } else {
                    return imageUrl;
                }
            }
            return "https://lawlietbot.xyz/cdn/" + boardType.getDomain() + "/" + id + "." + fileExt;
        }
        return imageUrl;
    }

}
