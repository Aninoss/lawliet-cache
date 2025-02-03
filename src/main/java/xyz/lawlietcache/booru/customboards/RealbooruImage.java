package xyz.lawlietcache.booru.customboards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RealbooruImage implements BoardImage {

    private long id;
    private String directory;
    @JsonProperty("file_url")
    private String fileUrl;
    private int height;
    private String tags;
    private int width;
    private int score;
    @JsonProperty("change")
    private long change; // timestamp in seconds

    public String getFileUrl() {
        return fileUrl;
    }

    public String getDirectory() {
        return directory;
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
        return Rating.EXPLICIT;
    }

    @Override
    public List<String> getTags() {
        return Collections.unmodifiableList(Arrays.asList(tags.split(" ")));
    }

    @Override
    @JsonIgnore
    public String getURL() {
        return getFileUrl();
    }

    // Doesn't implement it, lol.
    @Override
    @JsonIgnore
    public boolean hasChildren() {
        return false;
    }

    // Doesn't implement it
    @Override
    public boolean isPending() {
        return false;
    }

    @Override
    public long getCreationMillis() {
        return change * 1000;
    }

}
