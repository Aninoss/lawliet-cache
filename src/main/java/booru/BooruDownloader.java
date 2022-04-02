package booru;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import core.WebCache;
import net.kodehawa.lib.imageboards.ImageBoard;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;
import net.kodehawa.lib.imageboards.entities.exceptions.QueryFailedException;
import net.kodehawa.lib.imageboards.entities.exceptions.QueryParseException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import util.NSFWUtil;

public class BooruDownloader {

    private final static Logger LOGGER = LoggerFactory.getLogger(BooruDownloader.class);

    private final BooruFilter booruFilter;
    private final OkHttpClient client;
    private final WebCache webCache;
    private final Random random = new Random();
    private final JedisPool jedisPool;
    private final BooruTester booruTester;
    private final int mediaServerCooldown;
    private final int mediaServerMaxShards;

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
        this.booruTester = new BooruTester(webCache, jedisPool);
        this.mediaServerCooldown = Integer.parseInt(System.getenv("MS_COOLDOWN_MILLIS"));
        this.mediaServerMaxShards = Integer.parseInt(System.getenv("MS_MAX_SHARDS"));
        ImageBoard.setUserAgent(WebCache.USER_AGENT);
    }

    public List<BooruChoice> getTags(String domain, String search) {
        if (search.equals("+")) {
            search = "";
        }

        BoardType boardType = BoardType.fromDomain(domain);
        if (boardType == null) {
            throw new NoSuchElementException("No such image board");
        }

        return boardType.retrieveAutoComplete(webCache, search);
    }

    public BooruImage getPicture(long guildId, String domain, String searchKeys, boolean animatedOnly,
                                 boolean explicit, boolean canBeVideo, List<String> filters, List<String> skippedResults,
                                 boolean test) {
        searchKeys = NSFWUtil.filterPornSearchKey(searchKeys, filters);
        BoardType boardType = BoardType.fromDomain(domain);
        if (boardType == null) {
            throw new NoSuchElementException("No such image board");
        }

        if (test) {
            return booruTester.get(boardType) ? new BooruImage() : null;
        } else {
            searchKeys = searchKeys.replaceAll("[,;+]", " ")
                    .replaceAll(" {2,}", " ")
                    .toLowerCase();
            if (!canBeVideo) {
                if (boardType == BoardType.E621 || boardType == BoardType.E926) {
                    searchKeys = searchKeys.replaceAll("\\bwebm\\b", "animated");
                } else {
                    searchKeys = searchKeys.replaceAll("\\bvideo\\b", "animated_gif");
                }
            }

            return getPicture(guildId, boardType, searchKeys, animatedOnly, explicit, canBeVideo, 2, false, filters, skippedResults);
        }
    }

    private BooruImage getPicture(long guildId, BoardType boardType, String searchKeys, boolean animatedOnly,
                                  boolean explicit, boolean canBeVideo, int remaining, boolean softMode,
                                  List<String> filters, List<String> skippedResults
    ) {
        StringBuilder finalSearchKeys = new StringBuilder(softMode ? (searchKeys.replace(" ", "~ ") + "~") : searchKeys);
        List<String> visibleSearchKeysList = List.of(finalSearchKeys.toString().split(" "));

        if (boardType.getMaxTags() < 0) {
            for (String filter : filters) {
                finalSearchKeys.append(" -").append(filter);
            }
        }

        if (boardType.getMaxTags() >= 0) {
            finalSearchKeys = new StringBuilder(reduceTags(finalSearchKeys.toString(), boardType.getMaxTags() - 1));
            visibleSearchKeysList = visibleSearchKeysList.subList(0, Math.min(visibleSearchKeysList.size(), boardType.getMaxTags() - 1));
        }
        if (boardType == BoardType.DANBOORU) {
            finalSearchKeys.append(" -filetype:zip");
        }
        if (!canBeVideo) {
            if (boardType == BoardType.E621 || boardType == BoardType.E926) {
                finalSearchKeys.append(" -webm");
            } else {
                finalSearchKeys.append(" -video");
            }
        }

        int count = Math.min(20_000 / boardType.getMaxLimit() * boardType.getMaxLimit(), boardType.count(webCache, jedisPool, finalSearchKeys.toString(), true));
        if (count == 0) {
            if (!softMode) {
                return getPicture(guildId, boardType, searchKeys.replace(" ", "_"), animatedOnly, explicit, canBeVideo, remaining, true, filters, skippedResults);
            } else if (remaining > 0) {
                if (searchKeys.contains(" ")) {
                    return getPicture(guildId, boardType, searchKeys.replace(" ", "_"), animatedOnly, explicit, canBeVideo, remaining - 1, false, filters, skippedResults);
                } else if (searchKeys.contains("_")) {
                    return getPicture(guildId, boardType, searchKeys.replace("_", " "), animatedOnly, explicit, canBeVideo, remaining - 1, false, filters, skippedResults);
                }
            }

            return null;
        }

        int shift = count >= 19_000 ? 2000 : 0;
        int page = (shift + random.nextInt(count - shift)) / boardType.getMaxLimit();

        return getPictureOnPage(guildId, boardType, finalSearchKeys.toString(), page, animatedOnly, explicit, canBeVideo,
                filters, skippedResults, visibleSearchKeysList
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

    private BooruImage getPictureOnPage(long guildId, BoardType boardType, String searchTerm, int page,
                                        boolean animatedOnly, boolean explicit, boolean canBeVideo, List<String> filters,
                                        List<String> skippedResults, List<String> usedSearchKeys
    ) {
        ImageBoard<? extends BoardImage> imageBoard = new ImageBoard<>(client, boardType.getBoard(), boardType.getBoardImageClass());
        List<? extends BoardImage> boardImages;
        try {
            try {
                boardImages = imageBoard.search(page, boardType.getMaxLimit(), searchTerm).blocking();
            } catch (RuntimeException e) {
                if (e.getCause() != null) {
                    throw e.getCause();
                } else {
                    throw e;
                }
            }
        } catch (QueryFailedException | QueryParseException | SocketTimeoutException e) {
            LOGGER.error("Failed to query {}", boardType.getDomain());
            return null;
        } catch (InterruptedException e) {
            //ignore
            return null;
        } catch (Throwable e) {
            LOGGER.error("Error in imageboard type {}", boardType.getDomain(), e);
            return null;
        }

        if (boardImages.isEmpty()) {
            return null;
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
                        (!contentType.isVideo() || canBeVideo) &&
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

        BooruImageMeta booruImageMeta = booruFilter.filter(guildId, boardType.name(), searchTerm, pornImages, skippedResults, pornImages.size() - 1);
        if (booruImageMeta != null) {
            return createBooruImage(boardType, booruImageMeta.getBoardImage(), booruImageMeta.getContentType(), guildId, usedSearchKeys);
        } else {
            return null;
        }
    }

    private BooruImage createBooruImage(BoardType boardType, BoardImage image, ContentType contentType, long guildId,
                                        List<String> usedSearchKeys
    ) {
        String imageUrl = image.getURL();
        String originalImageUrl = imageUrl;
        String pageUrl = boardType.getPageUrl(image.getId());
        if (boardType == BoardType.RULE34) {
            try (Jedis jedis = jedisPool.getResource()) {
                if (contentType.isVideo()) {
                    if (usesSharding(guildId)) {
                        String[] parts = imageUrl.substring(1).split("/");
                        int shard = getShard(parts[parts.length - 2], parts[parts.length - 1]);
                        imageUrl = translateVideoUrlToOwnCDN(System.getenv("MS_SHARD_" + shard), imageUrl);
                    }
                    jedis.incr("rule34_video");
                }
                jedis.incr("rule34_total");
            }
        }

        return new BooruImage()
                .setImageUrl(imageUrl)
                .setOriginalImageUrl(originalImageUrl)
                .setPageUrl(pageUrl)
                .setScore(image.getScore())
                .setInstant(Instant.ofEpochMilli(image.getCreationMillis()))
                .setTags(usedSearchKeys);
    }

    private boolean usesSharding(long guildId) {
        if (guildId <= 999) {
            return false;
        } else if (mediaServerCooldown <= 0) {
            return true;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            SetParams setParams = new SetParams();
            setParams.nx();
            setParams.px(mediaServerCooldown);
            if ("OK".equals(jedis.set("ms_cooldown", Instant.now().toString(), setParams))) {
                return true;
            }
        }
        return false;
    }

    private int getShard(String dir, String id) {
        return Math.abs(Objects.hash(dir, id)) % mediaServerMaxShards;
    }

    private String translateVideoUrlToOwnCDN(String targetDomain, String videoUrl) {
        String[] slashParts = videoUrl.split("/");
        return "https://" + targetDomain + "/media/rule34/" + slashParts[slashParts.length - 2] + "/" + slashParts[slashParts.length - 1] + "?s=" + slashParts[2].split("\\.")[0];
    }

}
