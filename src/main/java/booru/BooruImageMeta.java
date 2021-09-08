package booru;

import net.kodehawa.lib.imageboards.entities.BoardImage;

public class BooruImageMeta {

    private final String imageUrl;
    private final BoardImage boardImage;
    private final int score;
    private final ContentType contentType;

    public BooruImageMeta(String imageUrl, int score, BoardImage boardImage, ContentType contentType) {
        this.imageUrl = imageUrl;
        this.score = score;
        this.boardImage = boardImage;
        this.contentType = contentType;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getWeight() {
        return (int) Math.pow(score + 1, 2.5) * (imageUrl.endsWith("gif") ? 3 : 1);
    }

    public BoardImage getBoardImage() {
        return boardImage;
    }

    public ContentType getContentType() {
        return contentType;
    }

}
