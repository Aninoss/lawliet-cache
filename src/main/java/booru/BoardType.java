package booru;

import booru.counters.*;
import booru.customboards.RealbooruBoard;
import booru.customboards.RealbooruImage;
import core.WebCache;
import net.kodehawa.lib.imageboards.boards.Board;
import net.kodehawa.lib.imageboards.boards.DefaultBoards;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.impl.FurryImage;
import net.kodehawa.lib.imageboards.entities.impl.GelbooruImage;
import net.kodehawa.lib.imageboards.entities.impl.Rule34Image;
import net.kodehawa.lib.imageboards.entities.impl.SafebooruImage;

public enum BoardType {

    RULE34(
            "rule34.xxx",
            "https://rule34.xxx/index.php?page=post&s=view&id=",
            999,
            DefaultBoards.R34,
            Rule34Image.class,
            new Rule34Counter()
    ),

    GELBOORU(
            "gelbooru.com",
            "https://gelbooru.com/index.php?page=post&s=view&id=",
            999,
            DefaultBoards.GELBOORU,
            GelbooruImage.class,
            new GelbooruCounter()
    ),

    SAFEBOORU(
            "safebooru.org",
            "https://safebooru.org/index.php?page=post&s=view&id=",
            999,
            DefaultBoards.SAFEBOORU,
            SafebooruImage.class,
            new SafebooruCounter()
    ),

    REALBOORU(
            "realbooru.com",
            "https://realbooru.com/index.php?page=post&s=view&id=",
            999,
            new RealbooruBoard(),
            RealbooruImage.class,
            new RealbooruCounter()
    ),

    E621(
            "e621.net",
            "https://e621.net/posts/",
            320,
            DefaultBoards.E621,
            FurryImage.class,
            new E621Counter()
    );

    private final String domain;
    private final String pagePrefix;
    private final int maxLimit;
    private final Board board;
    private final Class<? extends BoardImage> boardImageClass;
    private final Counter counter;

    BoardType(String domain, String pagePrefix, int maxLimit, Board board, Class<? extends BoardImage> boardImageClass, Counter counter) {
        this.domain = domain;
        this.pagePrefix = pagePrefix;
        this.maxLimit = maxLimit;
        this.board = board;
        this.boardImageClass = boardImageClass;
        this.counter = counter;
    }

    public String getDomain() {
        return domain;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public String getPageUrl(int id) {
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

    public static BoardType fromDomain(String domain) {
        for (BoardType boardType : BoardType.values()) {
            if (boardType.getDomain().equalsIgnoreCase(domain)) {
                return boardType;
            }
        }
        return null;
    }

}
