package booru.customboards;

import net.kodehawa.lib.imageboards.boards.Board;

public class Rule34PahealBoard implements Board {

    @Override
    public String getScheme() {
        return "https";
    }

    @Override
    public String getHost() {
        return "rule34.paheal.net";
    }

    @Override
    public String getPath() {
        return "api/danbooru/find_posts/index.xml";
    }

    @Override
    public String getQuery() {
        return "";
    }

    @Override
    public String getPageMarker() {
        return "pid";
    }

    @Override
    public String getOuterObject() {
        return null;
    }

}
