package com.krugworld.steward;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Just enough JSON-schema building to keep {@link StewardTools} readable. */
final class Schema {

    private Schema() {
    }

    static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> stringEnum(String description, String... values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        m.put("enum", List.of(values));
        return m;
    }

    static Map<String, Object> number(String description) {
        return Map.of("type", "number", "description", description);
    }

    static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    static Map<String, Object> bool(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    static Map<String, Object> stringArray(String description, String... values) {
        Map<String, Object> items = values.length == 0 ? Map.of("type", "string")
                : Map.of("type", "string", "enum", List.of(values));
        return Map.of("type", "array", "description", description, "items", items);
    }

    static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", properties);
        m.put("required", List.of(required));
        return m;
    }

    static Map<String, Object> props(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
