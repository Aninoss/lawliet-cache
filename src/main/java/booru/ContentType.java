package booru;

import java.util.List;

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

    public static ContentType parseFromTags(List<String> tags) {
        boolean animated = false;
        boolean video = false;

        for (String tag : tags) {
            if (tag.equalsIgnoreCase("animated")) {
                animated = true;
            }
            if (tag.equalsIgnoreCase("video") || tag.equalsIgnoreCase("webm")) {
                video = true;
            }
        }

        return new ContentType(animated, video);
    }

}
