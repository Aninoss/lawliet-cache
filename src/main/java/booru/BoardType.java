package booru;

import booru.autocomplete.*;
import booru.counters.*;
import booru.customboards.*;
import core.WebCache;
import net.kodehawa.lib.imageboards.ImageBoard;
import net.kodehawa.lib.imageboards.boards.Board;
import net.kodehawa.lib.imageboards.boards.DefaultBoards;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.impl.*;
import redis.clients.jedis.JedisPool;

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
            ImageBoard.ResponseFormat.JSON
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
            ImageBoard.ResponseFormat.JSON
    ),

    REALBOORU(
            "realbooru.com",
            "https://realbooru.com/index.php?page=post&s=view&id=",
            999,
            new RealbooruBoard(),
            RealbooruImage.class,
            new RealbooruCounter(),
            -1,
            new DefaultAutoComplete("realbooru.com"),
            ImageBoard.ResponseFormat.JSON
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
            ImageBoard.ResponseFormat.JSON
    ),

    KONACHAN(
            "konachan.com",
            "https://konachan.com/post/show/",
            999,
            DefaultBoards.KONACHAN,
            KonachanImage.class,
            new KonachanCounter(),
            6,
            new EmptyAutoComplete(),
            ImageBoard.ResponseFormat.JSON
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
            ImageBoard.ResponseFormat.JSON
    ),

    GELBOORU(
            "gelbooru.com",
            "https://gelbooru.com/index.php?page=post&s=view&id=",
            100,
            DefaultBoards.GELBOORU,
            GelbooruImage.class,
            new GelbooruCounter(),
            -1,
            new GelbooruAutoComplete(),
            ImageBoard.ResponseFormat.JSON
    ),

    E926(
            "e926.net",
            "https://e926.net/posts/",
            320,
            DefaultBoards.E926,
            FurryImage.class,
            new E926Counter(),
            40,
            new FurryAutoComplete("e926.net"),
            ImageBoard.ResponseFormat.JSON
    ),

    RULE34_PAHEAL(
            "rule34.paheal.net",
            "https://rule34.paheal.net/post/view/",
            100,
            new Rule34PahealBoard(),
            Rule34PahealImage.class,
            new Rule34PahealCounter(),
            3,
            new Rule34PahealAutoComplete(),
            ImageBoard.ResponseFormat.XML
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

    BoardType(String domain, String pagePrefix, int maxLimit, Board board, Class<? extends BoardImage> boardImageClass,
              Counter counter, int maxTags, BooruAutoComplete autoComplete, ImageBoard.ResponseFormat responseFormat) {
        this.domain = domain;
        this.pagePrefix = pagePrefix;
        this.maxLimit = maxLimit;
        this.board = board;
        this.boardImageClass = boardImageClass;
        this.counter = counter;
        this.maxTags = maxTags;
        this.autoComplete = autoComplete;
        this.responseFormat = responseFormat;
    }

    public String getDomain() {
        return domain;
    }

    public int getMaxLimit() {
        return maxLimit;
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
}
