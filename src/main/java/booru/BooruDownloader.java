package booru;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import core.ConsistentHash;
import core.Program;
import core.WebCache;
import net.kodehawa.lib.imageboards.ImageBoard;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;
import net.kodehawa.lib.imageboards.entities.exceptions.QueryFailedException;
import net.kodehawa.lib.imageboards.entities.exceptions.QueryParseException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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
    private final BooruTester booruTester;
    private final ConsistentHash<Integer> consistentHash;

    public BooruDownloader(WebCache webCache, JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.booruFilter = new BooruFilter(jedisPool);
        this.client = webCache.getClient().newBuilder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    request = request.newBuilder()
                            .url(String.format("http://localhost:%s/api/cached_proxy", System.getenv("PORT")))
                            .addHeader("Authorization", System.getenv("AUTH"))
                            .addHeader("X-Proxy-Url", request.url().toString())
                            .addHeader("X-Proxy-Minutes", "15")
                            .get()
                            .build();
                    return chain.proceed(request);
                })
                .build();
        this.webCache = webCache;
        this.booruTester = new BooruTester(webCache, jedisPool);
        ImageBoard.setUserAgent(WebCache.USER_AGENT);

        int mediaServerMaxShards = Integer.parseInt(System.getenv("MS_MAX_SHARDS"));
        List<Integer> mediaServerIndexes = IntStream.range(0, mediaServerMaxShards).boxed().collect(Collectors.toList());
        this.consistentHash = new ConsistentHash<>(mediaServerIndexes, 10);
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
                                 boolean mustBeExplicit, boolean canBeVideo, List<String> filters,
                                 List<String> strictFilters, List<String> skippedResults, boolean test) {
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

            return getPicture(guildId, boardType, searchKeys, animatedOnly, mustBeExplicit, canBeVideo, 2,
                    false, filters, strictFilters, skippedResults);
        }
    }

    private BooruImage getPicture(long guildId, BoardType boardType, String searchKeys, boolean animatedOnly,
                                  boolean mustBeExplicit, boolean canBeVideo, int remaining, boolean softMode,
                                  List<String> filters, List<String> strictFilters, List<String> skippedResults
    ) {
        StringBuilder finalSearchKeys = new StringBuilder(softMode ? (searchKeys.replace(" ", "~ ") + "~") : searchKeys);
        List<String> visibleSearchKeysList = List.of(finalSearchKeys.toString().split(" "));

        if (boardType.getMaxTags() < 0) {
            for (String filter : filters) {
                finalSearchKeys.append(" -").append(filter);
            }
            for (String strictFilter : strictFilters) {
                finalSearchKeys.append(" -").append(strictFilter);
            }
            if (mustBeExplicit) {
                finalSearchKeys.append(" rating:explicit");
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

        String finalSearchKeysString = finalSearchKeys.toString();
        int count = Math.min(20_000 / boardType.getMaxLimit() * boardType.getMaxLimit(), boardType.count(webCache, jedisPool, finalSearchKeysString, true));
        if (count == 0) {
            if (!softMode) {
                return getPicture(guildId, boardType, searchKeys.replace(" ", "_"), animatedOnly, mustBeExplicit, canBeVideo, remaining, true, filters, strictFilters, skippedResults);
            } else if (remaining > 0) {
                if (searchKeys.contains(" ")) {
                    return getPicture(guildId, boardType, searchKeys.replace(" ", "_"), animatedOnly, mustBeExplicit, canBeVideo, remaining - 1, false, filters, strictFilters, skippedResults);
                } else if (searchKeys.contains("_")) {
                    return getPicture(guildId, boardType, searchKeys.replace("_", " "), animatedOnly, mustBeExplicit, canBeVideo, remaining - 1, false, filters, strictFilters, skippedResults);
                }
            }

            return null;
        }

        int shift = count >= 19_000 ? 2000 : 0;
        int page = (shift + random.nextInt(count - shift)) / boardType.getMaxLimit();

        return getPictureOnPage(guildId, boardType, finalSearchKeysString, page, animatedOnly, mustBeExplicit,
                canBeVideo, filters, strictFilters, skippedResults, visibleSearchKeysList
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
                                        boolean animatedOnly, boolean mustBeExplicit, boolean canBeVideo,
                                        List<String> filters, List<String> strictFilters, List<String> skippedResults,
                                        List<String> usedSearchKeys
    ) {
        ImageBoard<? extends BoardImage> imageBoard = new ImageBoard<>(client, boardType.getBoard(), boardType.getBoardImageClass());
        List<? extends BoardImage> boardImages;
        try {
            try {
                boardImages = imageBoard.search(page, boardType.getMaxLimit(), searchTerm)
                        .blocking();
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

        int[] passingRestrictions = new int[10];
        for (BoardImage boardImage : boardImages) {
            String fileUrl = boardImage.getURL();
            if (fileUrl != null) {
                int score = boardImage.getScore();
                ContentType contentType = ContentType.parseFromUrl(fileUrl);
                boolean isExplicit = boardImage.getRating() == Rating.EXPLICIT;
                boolean notPending = !boardImage.isPending();
                long created = boardImage.getCreationMillis();
                boolean blocked = blockSet.contains(fileUrl);

                if (!Program.isProductionMode()) {
                    if (contentType != null) passingRestrictions[0]++;
                    if (contentType != null && (!animatedOnly || contentType.isAnimated())) passingRestrictions[1]++;
                    if (contentType != null && (!contentType.isVideo() || canBeVideo)) passingRestrictions[2]++;
                    if (score >= 0) passingRestrictions[3]++;
                    if (NSFWUtil.tagListAllowed(boardImage.getTags(), filters, strictFilters)) passingRestrictions[4]++;
                    if (!mustBeExplicit || isExplicit) passingRestrictions[5]++;
                    if (notPending) passingRestrictions[6]++;
                    if (created <= maxPostDate) passingRestrictions[7]++;
                    if (!blocked) passingRestrictions[8]++;
                }

                if (contentType != null &&
                        (!animatedOnly || contentType.isAnimated()) &&
                        (!contentType.isVideo() || canBeVideo) &&
                        score >= 0 &&
                        NSFWUtil.tagListAllowed(boardImage.getTags(), filters, strictFilters) &&
                        (!mustBeExplicit || isExplicit) &&
                        notPending &&
                        created <= maxPostDate &&
                        !blocked
                ) {
                    passingRestrictions[9]++;
                    pornImages.add(new BooruImageMeta(fileUrl, score, boardImage, contentType));
                }
            }
        }

        if (!Program.isProductionMode()) {
            LOGGER.info("Passing restrictions: {}", passingRestrictions);
        }

        BooruImageMeta booruImageMeta = booruFilter.filter(guildId, boardType.name(), searchTerm, pornImages, skippedResults, pornImages.size() - 1);
        if (booruImageMeta != null) {
            return createBooruImage(boardType, booruImageMeta.getBoardImage(), booruImageMeta.getContentType(), usedSearchKeys);
        } else {
            return null;
        }
    }

    private BooruImage createBooruImage(BoardType boardType, BoardImage image, ContentType contentType,
                                        List<String> usedSearchKeys
    ) {
        String imageUrl = image.getURL();
        String originalImageUrl = imageUrl;
        String pageUrl = boardType.getPageUrl(image.getId());
        try (Jedis jedis = jedisPool.getResource()) {
            if (contentType.isVideo()) {
                if (boardType == BoardType.RULE34) {
                    String[] parts = imageUrl.substring(1).split("/");
                    int shard = getShard(parts[parts.length - 2], parts[parts.length - 1]);
                    imageUrl = translateVideoUrlToOwnCDN(System.getenv("MS_SHARD_" + shard), imageUrl);
                }
                jedis.incr(boardType.name().toLowerCase() + "_video");
            }
            jedis.incr(boardType.name().toLowerCase() + "_total");
        }

        return new BooruImage()
                .setImageUrl(imageUrl)
                .setOriginalImageUrl(originalImageUrl)
                .setPageUrl(pageUrl)
                .setScore(image.getScore())
                .setInstant(Instant.ofEpochMilli(image.getCreationMillis()))
                .setTags(usedSearchKeys);
    }

    private int getShard(String dir, String id) {
        String key = dir + "/" + id;
        return consistentHash.get(key);
    }

    private String translateVideoUrlToOwnCDN(String targetDomain, String videoUrl) {
        String[] slashParts = videoUrl.split("/");
        return "https://" + targetDomain + "/media/rule34/" + slashParts[slashParts.length - 2] + "/" + slashParts[slashParts.length - 1] + "?s=" + slashParts[2].split("\\.")[0];
    }

}
