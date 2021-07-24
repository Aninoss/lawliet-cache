package booru;

import net.kodehawa.lib.imageboards.entities.BoardImage;

public class BooruImageMeta {

    private final String imageUrl;
    private final BoardImage boardImage;
    private final int score;

    public BooruImageMeta(String imageUrl, int score, BoardImage boardImage) {
        this.imageUrl = imageUrl;
        this.score = score;
        this.boardImage = boardImage;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public int getWeight() {
        return (int) Math.pow(score + 1, 2.5) * (imageUrl.endsWith("gif") ? 2 : 1);
    }

    public BoardImage getBoardImage() {
        return boardImage;
    }

}
