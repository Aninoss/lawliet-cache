package booru.customboards;

import net.kodehawa.lib.imageboards.boards.Board;

public class Rule34Board implements Board {

    @Override
    public String getScheme() {
        return "https";
    }

    @Override
    public String getHost() {
        return "api.rule34.xxx";
    }

    @Override
    public String getPath() {
        return "index.php";
    }

    @Override
    public String getQuery() {
        return "page=dapi&s=post&q=index&json=1";
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
