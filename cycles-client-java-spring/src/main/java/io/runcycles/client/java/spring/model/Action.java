package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Describes the action being budgeted.
 * Mirrors the server's {@code Action} DTO.
 */
public class Action {
    private final String kind;
    private final String name;
    private final List<String> tags;

    public Action(String kind, String name, List<String> tags) {
        this.kind = kind;
        this.name = name;
        this.tags = tags;
    }

    /**
     * Deserializes an {@code Action} from a raw API response map.
     *
     * @param map the action section of the response, or {@code null}
     * @return the parsed {@code Action}, or {@code null} if the input is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static Action fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new Action(
                map.get("kind") instanceof String s ? s : null,
                map.get("name") instanceof String s ? s : null,
                map.get("tags") instanceof List<?> l ? (List<String>) l : null
        );
    }

    public String getKind() { return kind; }
    public String getName() { return name; }
    public List<String> getTags() { return tags; }

    /**
     * Serializes this action to a map suitable for the API request body.
     *
     * @return a mutable map of non-null fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        if (kind != null) map.put("kind", kind);
        if (name != null) map.put("name", name);
        if (tags != null) map.put("tags", tags);
        return map;
    }

    @Override
    public String toString() {
        return "Action{kind='" + kind + '\'' +
                ", name='" + name + '\'' +
                ", tags=" + tags + '}';
    }
}
