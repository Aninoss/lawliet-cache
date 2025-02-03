package xyz.lawlietcache.pixiv;

import com.github.hanshsieh.pixivj.model.IllustType;
import com.github.hanshsieh.pixivj.model.SearchedIllusts;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class SearchedIllustsAdapter extends TypeAdapter<SearchedIllusts> {

    private static final TypeAdapter<OffsetDateTime> offsetDateTimeAdapter = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, OffsetDateTime value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public OffsetDateTime read(JsonReader in) throws IOException {
            String dateString = in.nextString();
            try {
                return OffsetDateTime.parse(dateString);
            } catch (DateTimeParseException e) {
                throw new JsonParseException("Failed to parse OffsetDateTime: " + dateString, e);
            }
        }
    };
    private static final Gson delegateGson = new GsonBuilder()
            .registerTypeAdapter(IllustType.class, new TypeAdapter<IllustType>() {
                @Override
                public void write(JsonWriter out, IllustType value) throws IOException {
                    out.value(value.name());
                }
                @Override
                public IllustType read(JsonReader in) throws IOException {
                    String name = in.nextString();
                    try {
                        return IllustType.valueOf(name.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new JsonParseException("Unknown IllustType: " + name, e);
                    }
                }
            })
            .registerTypeAdapter(OffsetDateTime.class, offsetDateTimeAdapter)
            .create();

    @Override
    public void write(JsonWriter out, SearchedIllusts value) throws IOException {
        delegateGson.toJson(value, SearchedIllusts.class, out);
    }

    @Override
    public SearchedIllusts read(JsonReader in) throws IOException {
        JsonElement element = JsonParser.parseReader(in);
        JsonObject jsonObject = element.getAsJsonObject();
        return delegateGson.fromJson(jsonObject, SearchedIllusts.class);
    }

}