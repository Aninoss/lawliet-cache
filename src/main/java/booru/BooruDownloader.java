package booru;

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

import java.io.InterruptedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BooruDownloader {

    private final static Logger LOGGER = LoggerFactory.getLogger(BooruDownloader.class);
    public static final String REPORTS_KEY = "reports";

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
                            .addHeader("X-Proxy-Minutes", "30")
                            .get()
                            .build();
                    return chain.proceed(request);
                })
                .cache(null)
                .build();
        this.webCache = webCache;
        this.booruTester = new BooruTester(webCache, jedisPool);
        ImageBoard.setUserAgent(WebCache.USER_AGENT);

        int mediaServerMaxShards = Integer.parseInt(System.getenv("MS_MAX_SHARDS"));
        List<Integer> mediaServerIndexes = IntStream.range(0, mediaServerMaxShards).boxed().collect(Collectors.toList());
        this.consistentHash = new ConsistentHash<>(mediaServerIndexes, 10);
    }

    public List<BooruChoice> getTags(String domain, String search) throws BooruException {
        boolean negativeTag = search.startsWith("-");
        if (negativeTag) {
            search = search.substring(1);
        }

        if (search.equals("+")) {
            search = "";
        }

        BoardType boardType = BoardType.fromDomain(domain);
        if (boardType == null) {
            throw new BooruException("No image board for domain " + domain);
        }

        return boardType.retrieveAutoComplete(webCache, search).stream()
                .map(choice -> {
                    if (negativeTag) {
                        return new BooruChoice()
                                .setName("-" + choice.getName())
                                .setValue("-" + choice.getValue());
                    } else {
                        return choice;
                    }
                })
                .collect(Collectors.toList());
    }

    public BooruImage getPicture(long guildId, String domain, String searchKeys, boolean animatedOnly,
                                 boolean mustBeExplicit, boolean canBeVideo, List<String> filters,
                                 List<String> strictFilters, List<String> skippedResults, boolean test) throws BooruException {
        BoardType boardType = BoardType.fromDomain(domain);
        if (boardType == null) {
            throw new BooruException("No image board for domain " + domain);
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
    ) throws BooruException {
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
            int reduce = (boardType == BoardType.DANBOORU ? 1 : 0) + (!canBeVideo ? 1 : 0);
            finalSearchKeys = new StringBuilder(reduceTags(finalSearchKeys.toString(), boardType.getMaxTags() - reduce));
            visibleSearchKeysList = visibleSearchKeysList.subList(0, Math.min(visibleSearchKeysList.size(), boardType.getMaxTags() - reduce));
        }
        if (boardType == BoardType.DANBOORU) {
            finalSearchKeys.append(" -ugoira");
        }
        if (!canBeVideo) {
            if (boardType == BoardType.E621 || boardType == BoardType.E926 || boardType == BoardType.RULE34_PAHEAL) {
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
        } else if (count < 0) {
            throw new BooruException();
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
    ) throws BooruException {
        ImageBoard<? extends BoardImage> imageBoard = new ImageBoard<>(client, boardType.getBoard(),
                boardType.getResponseFormat(), boardType.getBoardImageClass());
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
        } catch (QueryParseException e) {
            throw new BooruException("Failed to query " + boardType.getDomain());
        } catch (QueryFailedException | InterruptedException | InterruptedIOException e) {
            throw new BooruException();
        } catch (Throwable e) {
            throw new BooruException("Error in imageboard type " + boardType.getDomain(), e);
        }

        if (boardImages == null || boardImages.isEmpty()) {
            return null;
        }

        ArrayList<BooruImageMeta> pornImages = new ArrayList<>();
        long maxPostDate = System.currentTimeMillis() - Duration.ofDays(3).toMillis();
        Set<String> blockSet;
        try (Jedis jedis = jedisPool.getResource()) {
            blockSet = jedis.hgetAll(REPORTS_KEY).keySet();
        }

        int[] passingRestrictions = new int[9];
        for (BoardImage boardImage : boardImages) {
            String fileUrl = boardImage.getURL();
            if (fileUrl != null) {
                int score = boardImage.getScore();
                boolean isExplicit = boardImage.getRating() == Rating.EXPLICIT;
                boolean notPending = !boardImage.isPending();
                long created = boardImage.getCreationMillis();
                boolean blocked = blockSet.contains(fileUrl);

                ContentType contentType = ContentType.parseFromUrl(fileUrl);
                if (contentType == null) {
                    contentType = ContentType.parseFromTags(boardImage.getTags());
                }

                if (!Program.isProductionMode()) {
                    if (!animatedOnly || contentType.isAnimated()) passingRestrictions[0]++;
                    if (!contentType.isVideo() || canBeVideo) passingRestrictions[1]++;
                    if (score >= 0) passingRestrictions[2]++;
                    if (!NSFWUtil.containsFilterTags(boardImage.getTags(), filters, strictFilters)) passingRestrictions[3]++;
                    if (!mustBeExplicit || isExplicit) passingRestrictions[4]++;
                    if (notPending) passingRestrictions[5]++;
                    if (created <= maxPostDate) passingRestrictions[6]++;
                    if (!blocked) passingRestrictions[7]++;
                }

                if ((!animatedOnly || contentType.isAnimated()) &&
                        (!contentType.isVideo() || canBeVideo) &&
                        score >= 0 &&
                        !NSFWUtil.containsFilterTags(boardImage.getTags(), filters, strictFilters) &&
                        (!mustBeExplicit || isExplicit) &&
                        notPending &&
                        created <= maxPostDate &&
                        !blocked
                ) {
                    passingRestrictions[8]++;
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
                    String[] parts = imageUrl.split("/");
                    int shard = getShard(parts[parts.length - 2], parts[parts.length - 1]);
                    imageUrl = rule34VideoUrlToOwnCDN(System.getenv("MS_SHARD_" + shard), imageUrl);
                } else if (boardType == BoardType.DANBOORU) {
                    String[] parts = imageUrl.split("/");
                    int shard = getShard(parts[parts.length - 3] + "/" + parts[parts.length - 2], parts[parts.length - 1]);
                    imageUrl = danbooruVideoUrlToOwnCDN(System.getenv("MS_SHARD_" + shard), imageUrl);
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

    private String rule34VideoUrlToOwnCDN(String targetDomain, String videoUrl) {
        String[] slashParts = videoUrl.split("/");
        return "https://" + targetDomain + "/media/rule34/" + slashParts[slashParts.length - 2] + "/" + slashParts[slashParts.length - 1] + "?s=" + slashParts[2].split("\\.")[0];
    }

    private String danbooruVideoUrlToOwnCDN(String targetDomain, String videoUrl) {
        String[] slashParts = videoUrl.split("/");
        return "https://" + targetDomain + "/media/danbooru/" + slashParts[slashParts.length - 3] + "/" + slashParts[slashParts.length - 2] + "/" + slashParts[slashParts.length - 1] + "?s=" + slashParts[2].split("\\.")[0];
    }

}
