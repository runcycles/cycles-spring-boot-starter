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

    @Override
    public String toString() {
        return "Action{kind='" + kind + '\'' +
                ", name='" + name + '\'' +
                ", tags=" + tags + '}';
    }
}
