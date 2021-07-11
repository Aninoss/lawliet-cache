package booru;

public class BooruImageMeta {

    private final String imageUrl;
    private final int index;
    private final long score;

    public BooruImageMeta(String imageUrl, long score, int index) {
        this.imageUrl = imageUrl;
        this.score = score;
        this.index = index;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public long getWeight() {
        return (long) Math.pow(score + 1, 2.5) * (imageUrl.endsWith("gif") ? 2 : 1);
    }

    public int getIndex() {
        return index;
    }

}
