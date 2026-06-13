package scheduler.api.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

public final class GsonConfig {
    private GsonConfig() {}

    public static Gson create() {
        return createBuilder().create();
    }

    public static Gson createPretty() {
        return createBuilder().setPrettyPrinting().create();
    }

    private static GsonBuilder createBuilder() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, instantAdapter())
                .registerTypeAdapter(Duration.class, durationAdapter())
                .registerTypeAdapter(LocalTime.class, localTimeAdapter());
    }

    private static TypeAdapter<Instant> instantAdapter() {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, Instant value) throws IOException {
                if (value == null) {
                    out.nullValue();
                } else {
                    out.value(value.toString());
                }
            }

            @Override
            public Instant read(JsonReader in) throws IOException {
                if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return Instant.parse(in.nextString());
            }
        };
    }

    private static TypeAdapter<LocalTime> localTimeAdapter() {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, LocalTime value) throws IOException {
                if (value == null) {
                    out.nullValue();
                } else {
                    out.value(value.toString());
                }
            }

            @Override
            public LocalTime read(JsonReader in) throws IOException {
                if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return parseLocalTime(in.nextString());
            }
        };
    }

    /** Убирает пробелы/переносы внутри значения (частая опечатка при правке JSON вручную). */
    static LocalTime parseLocalTime(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LocalTime value is blank");
        }
        String normalized = raw.strip().replaceAll("\\s+", "");
        return LocalTime.parse(normalized);
    }

    private static TypeAdapter<Duration> durationAdapter() {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, Duration value) throws IOException {
                if (value == null) {
                    out.nullValue();
                } else {
                    out.value(value.toString());
                }
            }

            @Override
            public Duration read(JsonReader in) throws IOException {
                if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return Duration.parse(in.nextString());
            }
        };
    }
}
