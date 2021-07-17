package core;

public class RandomPickerRequest {

    private String tag;
    private long guildId;
    private int size;

    public String getTag() {
        return tag;
    }

    public RandomPickerRequest setTag(String tag) {
        this.tag = tag;
        return this;
    }

    public long getGuildId() {
        return guildId;
    }

    public RandomPickerRequest setGuildId(long guildId) {
        this.guildId = guildId;
        return this;
    }

    public int getSize() {
        return size;
    }

    public RandomPickerRequest setSize(int size) {
        this.size = size;
        return this;
    }

}
