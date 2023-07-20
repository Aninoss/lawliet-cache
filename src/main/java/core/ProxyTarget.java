package core;

public enum ProxyTarget {
    DANBOORU("danbooru.donmai.us"),
    E621("e621.net"),
    REDDIT("reddit.com");

    private final String domain;

    ProxyTarget(String domain) {
        this.domain = domain;
    }

    public String getDomain() {
        return domain;
    }

}
