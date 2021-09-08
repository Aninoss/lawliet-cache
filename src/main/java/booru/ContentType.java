package booru;

public class ContentType {

    private final boolean animated;
    private final boolean video;

    private ContentType(boolean animated, boolean video) {
        this.animated = animated;
        this.video = video;
    }

    public boolean isAnimated() {
        return animated;
    }

    public boolean isVideo() {
        return video;
    }

    public static ContentType parseFromUrl(String url) {
        String[] urlParts = url.toLowerCase().split("\\.");
        String ext = urlParts[urlParts.length - 1];

        return switch (ext) {
            case "jpeg", "jpg", "png", "bmp" -> new ContentType(false, false);
            case "gif" -> new ContentType(true, false);
            case "mp4", "avi", "webm" -> new ContentType(true, true);
            default -> null;
        };
    }

}
