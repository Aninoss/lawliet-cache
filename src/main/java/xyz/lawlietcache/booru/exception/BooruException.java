package xyz.lawlietcache.booru.exception;

import java.io.IOException;

public class BooruException extends IOException {

    public BooruException() {
    }

    public BooruException(String message) {
        super(message);
    }

    public BooruException(String message, Throwable cause) {
        super(message, cause);
    }

    public BooruException(Throwable cause) {
        super(cause);
    }

}
