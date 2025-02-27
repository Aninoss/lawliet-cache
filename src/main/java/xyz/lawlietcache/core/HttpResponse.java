package xyz.lawlietcache.core;

import java.io.Serial;
import java.io.Serializable;

public class HttpResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 2316701129633363577L;

    private String body;
    private int code;

    public HttpResponse setBody(String body) {
        this.body = body;
        return this;
    }

    public HttpResponse setCode(int code) {
        this.code = code;
        return this;
    }

    public String getBody() {
        return body;
    }

    public int getCode() {
        return code;
    }

}
