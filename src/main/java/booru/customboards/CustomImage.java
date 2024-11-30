package booru.customboards;

import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;

import java.util.List;

public class CustomImage implements BoardImage {

    private final long id;
    private final int width;
    private final int height;
    private final int score;
    private final Rating rating;
    private final List<String> tags;
    private final String url;
    private final boolean hasChildren;
    private final boolean isPending;
    private final long creationMillis;

    public CustomImage(long id, int width, int height, int score, Rating rating, List<String> tags, String url, boolean hasChildren, boolean isPending, long creationMillis) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.score = score;
        this.rating = rating;
        this.tags = tags;
        this.url = url;
        this.hasChildren = hasChildren;
        this.isPending = isPending;
        this.creationMillis = creationMillis;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getScore() {
        return score;
    }

    @Override
    public Rating getRating() {
        return rating;
    }

    @Override
    public List<String> getTags() {
        return tags;
    }

    @Override
    public String getURL() {
        return url;
    }

    @Override
    public boolean hasChildren() {
        return hasChildren;
    }

    @Override
    public boolean isPending() {
        return isPending;
    }

    @Override
    public long getCreationMillis() {
        return creationMillis;
    }

}
