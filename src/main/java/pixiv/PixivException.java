package pixiv;

import java.io.IOException;

public class PixivException extends IOException {

    public PixivException() {
    }

    public PixivException(String message) {
        super(message);
    }

    public PixivException(String message, Throwable cause) {
        super(message, cause);
    }

    public PixivException(Throwable cause) {
        super(cause);
    }

}
