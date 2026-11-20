package xyz.lawlietcache.booru;

import net.kodehawa.lib.imageboards.ImageBoard;
import net.kodehawa.lib.imageboards.boards.Board;
import net.kodehawa.lib.imageboards.boards.DefaultBoards;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.exceptions.QueryFailedException;
import net.kodehawa.lib.imageboards.entities.exceptions.QueryParseException;
import net.kodehawa.lib.imageboards.entities.impl.DanbooruImage;
import net.kodehawa.lib.imageboards.entities.impl.Rule34Image;
import net.kodehawa.lib.imageboards.entities.impl.SafebooruImage;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import xyz.lawlietcache.booru.autocomplete.BooruAutoComplete;
import xyz.lawlietcache.booru.autocomplete.DanbooruAutoComplete;
import xyz.lawlietcache.booru.autocomplete.DefaultAutoComplete;
import xyz.lawlietcache.booru.autocomplete.FurryAutoComplete;
import xyz.lawlietcache.booru.counters.*;
import xyz.lawlietcache.booru.customboards.*;
import xyz.lawlietcache.booru.exception.BooruException;
import xyz.lawlietcache.booru.exception.SilentBooruException;
import xyz.lawlietcache.booru.workaroundsearcher.RealbooruWorkaroundSearcher;
import xyz.lawlietcache.booru.workaroundsearcher.WorkaroundSearcher;
import xyz.lawlietcache.core.WebCache;

import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.List;

public enum BoardType {

    RULE34(
            "rule34.xxx",
            "https://rule34.xxx/index.php?page=post&s=view&id=",
            999,
            new Rule34Board(),
            Rule34Image.class,
            new Rule34Counter(),
            -1,
            new DefaultAutoComplete("api.rule34.xxx"),
            ImageBoard.ResponseFormat.JSON,
            null
    ),

    SAFEBOORU(
            "safebooru.org",
            "https://safebooru.org/index.php?page=post&s=view&id=",
            999,
            DefaultBoards.SAFEBOORU,
            SafebooruImage.class,
            new SafebooruCounter(),
            -1,
            new DefaultAutoComplete("safebooru.org"),
            ImageBoard.ResponseFormat.JSON,
            null
    ),

    REALBOORU(
            "realbooru.com",
            "https://realbooru.com/index.php?page=post&s=view&id=",
            42,
            new RealbooruBoard(),
            RealbooruImage.class,
            new RealbooruWorkaroundCounter(),
            40,
            new DefaultAutoComplete("realbooru.com"),
            ImageBoard.ResponseFormat.JSON,
            new RealbooruWorkaroundSearcher()
    ),

    E621(
            "e621.net",
            "https://e621.net/posts/",
            320,
            new E621Board(),
            CustomFurryImage.class,
            new E621Counter(),
            40,
            new FurryAutoComplete("e621.net"),
            ImageBoard.ResponseFormat.JSON,
            null
    ),

    DANBOORU(
            "danbooru.donmai.us",
            "https://danbooru.donmai.us/posts/",
            200,
            DefaultBoards.DANBOORU,
            DanbooruImage.class,
            null,
            10,
            new DanbooruAutoComplete(),
            ImageBoard.ResponseFormat.JSON,
            null
    );

    private final static Logger LOGGER = LoggerFactory.getLogger(BoardType.class);

    private final String domain;
    private final String pagePrefix;
    private final int maxLimit;
    private final Board board;
    private final Class<? extends BoardImage> boardImageClass;
    private final Counter counter;
    private final int maxTags;
    private final BooruAutoComplete autoComplete;
    private final ImageBoard.ResponseFormat responseFormat;
    private final WorkaroundSearcher workaroundSearcher;

    BoardType(String domain, String pagePrefix, int maxLimit, Board board, Class<? extends BoardImage> boardImageClass,
              Counter counter, int maxTags, BooruAutoComplete autoComplete, ImageBoard.ResponseFormat responseFormat, WorkaroundSearcher workaroundSearcher) {
        this.domain = domain;
        this.pagePrefix = pagePrefix;
        this.maxLimit = maxLimit;
        this.board = board;
        this.boardImageClass = boardImageClass;
        this.counter = counter;
        this.maxTags = maxTags;
        this.autoComplete = autoComplete;
        this.responseFormat = responseFormat;
        this.workaroundSearcher = workaroundSearcher;
    }

    public String getDomain() {
        return domain;
    }

    public int getMaxLimit() {
        return Math.min(maxLimit, 200);
    }

    public String getPageUrl(long id) {
        return pagePrefix + id;
    }

    public Board getBoard() {
        return board;
    }

    public Class<? extends BoardImage> getBoardImageClass() {
        return boardImageClass;
    }

    public int count(OkHttpClient client, WebCache webCache, JedisPool jedisPool, String tags, boolean withCache) {
        if (counter != null) {
            return counter.count(webCache, jedisPool, tags, withCache);
        }

        // Counted via the search request, since the counts endpoint is rate limited
        try {
            return search(client, webCache, tags, 0).size();
        } catch (BooruException e) {
            LOGGER.error("Booru count exception", e);
            return -1;
        }
    }

    public List<? extends BoardImage> search(OkHttpClient client, WebCache webCache, String searchTerm, int page) throws BooruException {
        List<? extends BoardImage> boardImages;
        if (workaroundSearcher != null) {
            boardImages = workaroundSearcher.search(page, searchTerm, webCache);
        } else {
            ImageBoard<? extends BoardImage> imageBoard = new ImageBoard<>(client, board, responseFormat, boardImageClass);
            try {
                try {
                    boardImages = imageBoard.search(page, getMaxLimit(), searchTerm)
                            .blocking();
                } catch (RuntimeException e) {
                    if (e.getCause() != null) {
                        throw e.getCause();
                    } else {
                        throw e;
                    }
                }
            } catch (QueryParseException e) {
                throw new BooruException("Failed to query " + domain);
            } catch (QueryFailedException | InterruptedException | InterruptedIOException e) {
                throw new SilentBooruException();
            } catch (Throwable e) {
                throw new BooruException("Error in imageboard type " + domain, e);
            }
        }

        return boardImages == null ? Collections.emptyList() : boardImages;
    }

    public List<BooruChoice> retrieveAutoComplete(WebCache webCache, String search) {
        return autoComplete.retrieve(webCache, search);
    }

    public int getMaxTags() {
        return maxTags;
    }

    public static BoardType fromDomain(String domain) {
        for (BoardType boardType : BoardType.values()) {
            if (boardType.getDomain().equalsIgnoreCase(domain)) {
                return boardType;
            }
        }
        return null;
    }

    public ImageBoard.ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public WorkaroundSearcher getWorkaroundSearcher() {
        return workaroundSearcher;
    }

}
