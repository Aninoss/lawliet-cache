package reddit;

import java.io.IOException;

public class RedditException extends IOException {

    public RedditException() {
    }

    public RedditException(String message) {
        super(message);
    }

    public RedditException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedditException(Throwable cause) {
        super(cause);
    }

}
