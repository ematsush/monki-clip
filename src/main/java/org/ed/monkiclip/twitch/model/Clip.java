package org.ed.monkiclip.twitch.model;

import com.google.api.client.util.DateTime;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.ed.monkiclip.Util.convertToRfc3339;

@Builder
@Getter
@ToString
public class Clip {

    private final String id;
    private final String url;
    private final String broadcasterName;
    private final String creatorName;
    private final String videoId;
    private final String gameId;
    private final String title;
    private final String thumbnailUrl;
    @JsonAdapter(LocalDateTimeAdapter.class)
    private final LocalDateTime createdAt;
    private final int viewCount;

    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            out.value(convertToRfc3339(value));
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            DateTime d = DateTime.parseRfc3339(in.nextString());
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(d.getValue()),
                    TimeZone.getDefault().toZoneId());
        }
    }
}
