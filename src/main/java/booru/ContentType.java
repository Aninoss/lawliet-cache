package booru;

public enum ContentType {

    NONE, IMAGE, ANIMATED;

    public static ContentType parseFromUrl(String url) {
        String[] urlParts = url.toLowerCase().split("\\.");
        String ext = urlParts[urlParts.length - 1];

        return switch (ext) {
            case "jpeg", "jpg", "png", "bmp" -> IMAGE;
            case "gif", "mp4", "avi", "webm" -> ANIMATED;
            default -> NONE;
        };
    }

}
