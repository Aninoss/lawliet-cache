package xyz.lawlietcache.booru.customboards;

import net.kodehawa.lib.imageboards.boards.Board;

public class E621Board implements Board {

    @Override
    public String getScheme() {
        return "https";
    }

    @Override
    public String getHost() {
        return "e621.net";
    }

    @Override
    public String getPath() {
        return "posts.json";
    }

    @Override
    public String getQuery() {
        return null;
    }

    @Override
    public String getPageMarker() {
        return "page";
    }

    @Override
    public String getOuterObject() {
        return null;
    }

}
