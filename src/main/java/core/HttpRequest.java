package core;

import java.io.Serializable;

public class HttpRequest implements Serializable {

    private String method;
    private String url;
    private String body;
    private String contentType;

    public String getMethod() {
        return method;
    }

    public HttpRequest setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public HttpRequest setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getBody() {
        return body;
    }

    public HttpRequest setBody(String body) {
        this.body = body;
        return this;
    }

    public String getContentType() {
        return contentType;
    }

    public HttpRequest setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

}
