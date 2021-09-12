package booru;

import booru.counters.*;
import booru.customboards.RealbooruBoard;
import booru.customboards.RealbooruImage;
import core.WebCache;
import net.kodehawa.lib.imageboards.boards.Board;
import net.kodehawa.lib.imageboards.boards.DefaultBoards;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.impl.*;

public enum BoardType {

    RULE34(
            "rule34.xxx",
            "https://rule34.xxx/index.php?page=post&s=view&id=",
            999,
            DefaultBoards.R34,
            Rule34Image.class,
            new Rule34Counter(),
            -1
    ),

    SAFEBOORU(
            "safebooru.org",
            "https://safebooru.org/index.php?page=post&s=view&id=",
            999,
            DefaultBoards.SAFEBOORU,
            SafebooruImage.class,
            new SafebooruCounter(),
            -1
    ),

    REALBOORU(
            "realbooru.com",
            "https://realbooru.com/index.php?page=post&s=view&id=",
            999,
            new RealbooruBoard(),
            RealbooruImage.class,
            new RealbooruCounter(),
            -1
    ),

    E621(
            "e621.net",
            "https://e621.net/posts/",
            320,
            DefaultBoards.E621,
            FurryImage.class,
            new E621Counter(),
            -1
    ),

    KONACHAN(
            "konachan.com",
            "https://konachan.com/post/show/",
            999,
            DefaultBoards.KONACHAN,
            KonachanImage.class,
            new KonachanCounter(),
            6
    ),

    DANBOORU(
            "danbooru.donmai.us",
            "https://danbooru.donmai.us/posts/",
            200,
            DefaultBoards.DANBOORU,
            DanbooruImage.class,
            new DanbooruCounter(),
            10
    ),

    E926(
            "e926.net",
            "https://e926.net/posts/",
            320,
            DefaultBoards.E926,
            FurryImage.class,
            new E926Counter(),
            -1
    );

    private final String domain;
    private final String pagePrefix;
    private final int maxLimit;
    private final Board board;
    private final Class<? extends BoardImage> boardImageClass;
    private final Counter counter;
    private final int maxTags;

    BoardType(String domain, String pagePrefix, int maxLimit, Board board, Class<? extends BoardImage> boardImageClass, Counter counter, int maxTags) {
        this.domain = domain;
        this.pagePrefix = pagePrefix;
        this.maxLimit = maxLimit;
        this.board = board;
        this.boardImageClass = boardImageClass;
        this.counter = counter;
        this.maxTags = maxTags;
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

    public int count(WebCache webCache, String tags) {
        return counter.count(webCache, tags);
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

}
