package xyz.lawlietcache.booru;

import net.kodehawa.lib.imageboards.ImageBoard;
import net.kodehawa.lib.imageboards.boards.Board;
import net.kodehawa.lib.imageboards.boards.DefaultBoards;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.impl.DanbooruImage;
import net.kodehawa.lib.imageboards.entities.impl.FurryImage;
import net.kodehawa.lib.imageboards.entities.impl.Rule34Image;
import net.kodehawa.lib.imageboards.entities.impl.SafebooruImage;
import redis.clients.jedis.JedisPool;
import xyz.lawlietcache.booru.autocomplete.BooruAutoComplete;
import xyz.lawlietcache.booru.autocomplete.DanbooruAutoComplete;
import xyz.lawlietcache.booru.autocomplete.DefaultAutoComplete;
import xyz.lawlietcache.booru.autocomplete.FurryAutoComplete;
import xyz.lawlietcache.booru.counters.*;
import xyz.lawlietcache.booru.customboards.RealbooruBoard;
import xyz.lawlietcache.booru.customboards.RealbooruImage;
import xyz.lawlietcache.booru.customboards.Rule34Board;
import xyz.lawlietcache.booru.workaroundsearcher.RealbooruWorkaroundSearcher;
import xyz.lawlietcache.booru.workaroundsearcher.WorkaroundSearcher;
import xyz.lawlietcache.core.WebCache;

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
            -1,
            new DefaultAutoComplete("realbooru.com"),
            ImageBoard.ResponseFormat.JSON,
            new RealbooruWorkaroundSearcher()
    ),

    E621(
            "e621.net",
            "https://e621.net/posts/",
            320,
            DefaultBoards.E621,
            FurryImage.class,
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
            new DanbooruCounter(),
            10,
            new DanbooruAutoComplete(),
            ImageBoard.ResponseFormat.JSON,
            null
    );

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

    public int count(WebCache webCache, JedisPool jedisPool, String tags, boolean withCache) {
        return counter.count(webCache, jedisPool, tags, withCache);
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
