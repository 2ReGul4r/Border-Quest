package net.borderquest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;

public final class Localization {

    private static final String DEFAULT_LOCALE = "en_us";
    private static final String LOCALE_PATH_FORMAT = "assets/borderquest/lang/%s.json";
    private static final Map<String, String> DEFAULT_STRINGS = loadLocale(DEFAULT_LOCALE);
    private static volatile Map<String, String> strings = DEFAULT_STRINGS;
    private static volatile String currentLocale = DEFAULT_LOCALE;

    private Localization() {
    }

    private static Map<String, String> loadLocale(String locale) {
        String path = String.format(LOCALE_PATH_FORMAT, locale);
        try (InputStream stream = Localization.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                return Collections.emptyMap();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                JsonObject json = new Gson().fromJson(reader, JsonObject.class);
                if (json == null) {
                    return Collections.emptyMap();
                }

                Map<String, String> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        map.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return Collections.unmodifiableMap(map);
            }
        } catch (IOException | JsonParseException e) {
            return Collections.emptyMap();
        }
    }

    public static synchronized void init(String locale) {
        if (locale == null || locale.isBlank()) {
            locale = DEFAULT_LOCALE;
        }
        if (locale.equals(currentLocale) && strings != null) {
            return;
        }

        Map<String, String> loaded = loadLocale(locale);
        strings = loaded.isEmpty() ? DEFAULT_STRINGS : loaded;
        currentLocale = locale;
    }

    public static String translate(String key, Object... args) {
        if (strings == null) {
            init(DEFAULT_LOCALE);
        }

        String pattern = strings.getOrDefault(key, DEFAULT_STRINGS.getOrDefault(key, key));
        if (args == null || args.length == 0) {
            return pattern;
        }

        try {
            return String.format(java.util.Locale.ROOT, pattern, args);
        } catch (IllegalFormatException e) {
            return pattern;
        }
    }
}
