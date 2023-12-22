package booru.customboards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Rule34PahealImage implements BoardImage {

    private long id;
    private String md5;
    private String file_name;
    private String file_url;
    private int height;
    private int width;
    private String preview_url;
    private int preview_height;
    private int preview_width;
    private String rating;
    private String date;
    private String tags;
    private String source;
    private int score;
    private String author;

    public String getMd5() {
        return md5;
    }

    public String getFileName() {
        return file_name;
    }

    public String getFileUrl() {
        if (file_url == null) {
            return null;
        }
        return file_url.replaceAll("%20-%20[^.]*", "");
    }

    public String getPreviewUrl() {
        return preview_url;
    }

    public int getPreviewHeight() {
        return preview_height;
    }

    public int getPreviewWidth() {
        return preview_width;
    }

    public String getDate() {
        return date;
    }

    public String getSource() {
        return source;
    }

    public String getAuthor() {
        return author;
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime localDateTime = LocalDateTime.parse(date.split("\\.")[0], formatter);
        return localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public void setFile_name(String file_name) {
        this.file_name = file_name;
    }

    public void setFile_url(String file_url) {
        this.file_url = file_url;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setPreview_url(String preview_url) {
        this.preview_url = preview_url;
    }

    public void setPreview_height(int preview_height) {
        this.preview_height = preview_height;
    }

    public void setPreview_width(int preview_width) {
        this.preview_width = preview_width;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
