package xyz.lawlietcache.core;

public enum ProxyTarget {

    DANBOORU("danbooru.donmai.us"),
    E621("e621.net"),
    FEEDBURNER("feeds.feedburner.com");

    private final String domain;

    ProxyTarget(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }

}
