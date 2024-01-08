package booru.customboards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RealbooruImage implements BoardImage {

    private final static Logger LOGGER = LoggerFactory.getLogger(RealbooruImage.class);

    private long id;
    private String directory;
    private String image;
    private String hash;
    private int height;
    private String tags;
    private int width;
    private int score;
    @JsonProperty("change")
    private long change; // timestamp in seconds

    public String getFileUrl() {
        if (hash == null) {
            LOGGER.error("Realbooru image no hash for file with id {}", id);
            return null;
        }

        String dir = hash.substring(0, 2) + "/" + hash.substring(2, 4);
        return "https://realbooru.com/images/" + dir + "/" + hash + "." + image.split("\\.")[1];
    }

    public String getDirectory() {
        return directory;
    }

    public String getImage() {
        return image;
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

    public String getHash() {
        return hash;
    }

}
