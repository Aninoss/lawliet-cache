package core;

public enum ProxyTarget {
    DANBOORU("danbooru.donmai.us", false),
    E621("e621.net", true),
    REDDIT("reddit.com", true);

    private final String domain;
    private final boolean allowWithoutProxy;

    ProxyTarget(String domain, boolean allowWithoutProxy) {
        this.domain = domain;
        this.allowWithoutProxy = allowWithoutProxy;
    }

    public String getDomain() {
        return domain;
    }

    public boolean allowWithoutProxy() {
        return allowWithoutProxy;
    }

}
