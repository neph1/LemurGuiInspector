package com.lemurguiinspector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A sparse, JSON-friendly node in the dumped GUI tree. Attributes are kept in
 * insertion order and null values are never stored, so the serialized output
 * only contains values that were actually set (defaults omitted).
 */
public class GuiNode {

    /** Path from the dumped root, e.g. "/0/2" (index among GUI-node children). */
    public final String path;

    /** Sparse attributes: type, elementId, pos, size, text, color, ... */
    public final Map<String, Object> attrs = new LinkedHashMap<>();

    public final List<GuiNode> children = new ArrayList<>();

    public GuiNode(String path) {
        this.path = path;
    }

    public GuiNode put(String key, Object value) {
        if (value != null) {
            attrs.put(key, value);
        }
        return this;
    }

    public Object get(String key) {
        return attrs.get(key);
    }

    /** Converts to a plain Map (attrs + "children") for serialization. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>(attrs);
        if (!children.isEmpty()) {
            List<Object> c = new ArrayList<>(children.size());
            for (GuiNode child : children) {
                c.add(child.toMap());
            }
            m.put("children", c);
        }
        return m;
    }

    @Override
    public String toString() {
        return attrs.getOrDefault("type", "?") + "[" + path + "]";
    }
}
