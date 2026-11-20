/*
 * Copyright 2017 Kodehawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.lawlietcache.booru.customboards;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.kodehawa.lib.imageboards.entities.BoardImage;
import net.kodehawa.lib.imageboards.entities.Rating;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * @author Kodehawa
 */
public class CustomFurryImage implements BoardImage {
    private long id;
    @JsonProperty("created_at")
    private String created_at;
    private String description;
    @JsonProperty("rating")
    private String rating;
    @JsonProperty("files")
    private Files files;
    @JsonProperty("stats")
    private Stats stats;
    @JsonProperty("has")
    private Has has;
    @JsonProperty("flags")
    private Flags flags;
    @JsonProperty("tags")
    private List<String> tags;

    @Override
    public boolean hasChildren() {
        return has != null && has.children;
    }

    //Image description
    public String getDescription() {
        return description;
    }

    public File getFile() {
        return files.original;
    }

    public File getMp4File() {
        if (files.video == null || files.video.variants == null) {
            return null;
        }
        return files.video.variants.mp4;
    }

    public File getLowResFile() {
        if (files.video == null || files.video.samples == null) {
            return null;
        }
        return files.video.samples.p480;
    }

    public Flags getFlags() {
        return flags;
    }

    static class Files {
        @JsonProperty("original")
        private File original;
        @JsonProperty("video")
        private Video video;
    }

    static class Video {
        @JsonProperty("variants")
        private Variants variants;
        @JsonProperty("samples")
        private Samples samples;
    }

    static class Variants {
        @JsonProperty("mp4")
        private File mp4;
    }

    static class Samples {
        @JsonProperty("480p")
        private File p480;
    }

    static class Stats {
        @JsonProperty("score")
        private Score score;
    }

    static class Has {
        @JsonProperty("children")
        private boolean children;
    }

    static class Score {
        private int up;
        private int down;
        private int total;

        public int getUp() {
            return up;
        }

        public int getDown() {
            return down;
        }

        public int getTotal() {
            return total;
        }
    }

    static class Flags {
        private boolean pending;
        private boolean flagged;
        private boolean note_locked;
        private boolean status_locked;
        private boolean rating_locked;
        private boolean deleted;

        public boolean isPending() {
            return pending;
        }

        public boolean isFlagged() {
            return flagged;
        }

        public boolean isNote_locked() {
            return note_locked;
        }

        public boolean isStatus_locked() {
            return status_locked;
        }

        public boolean isRating_locked() {
            return rating_locked;
        }

        public boolean isDeleted() {
            return deleted;
        }
    }

    public static class File {
        @JsonProperty("width")
        private int width;
        @JsonProperty("height")
        private int height;
        @JsonProperty("size")
        private int size;
        @JsonProperty("url")
        private String url;

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getSize() {
            return size;
        }

        public String getUrl() {
            return url;
        }
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public int getWidth() {
        return getFile().getWidth();
    }

    @Override
    public int getHeight() {
        return getFile().getHeight();
    }

    @Override
    public int getScore() {
        return stats.score.getTotal();
    }

    @Override
    @JsonIgnore
    public Rating getRating() {
        if (rating == null) {
            return null;
        }
        return switch (rating) {
            case "s" -> Rating.SAFE;
            case "q" -> Rating.QUESTIONABLE;
            case "e" -> Rating.EXPLICIT;
            default -> null;
        };
    }

    @Override
    @JsonIgnore
    public List<String> getTags() {
        return tags;
    }

    @Override
    public String getURL() {
        return getFile().getUrl();
    }

    @Override
    public boolean isPending() {
        return getFlags().isPending() || getFlags().isDeleted() || getFlags().isFlagged();
    }

    @Override
    public long getCreationMillis() {
        return ZonedDateTime.parse(created_at).toInstant().toEpochMilli();
    }
}
