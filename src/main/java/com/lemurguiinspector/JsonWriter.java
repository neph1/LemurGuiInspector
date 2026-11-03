package com.lemurguiinspector;

import java.util.List;
import java.util.Map;

/**
 * Minimal dependency-free JSON writer. Handles Map, List, String, Number,
 * Boolean and null. Numbers that are mathematically integral are printed
 * without a decimal point to keep the output compact.
 */
public final class JsonWriter {

    private JsonWriter() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, 0);
        return sb.toString();
    }

    public static String writePretty(Object value) {
        StringBuilder sb = new StringBuilder();
        writePretty(value, sb, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb, int indent) {
        writePretty(value, sb, indent);
    }

    private static void writePretty(Object value, StringBuilder sb, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Number) {
            writeNumber((Number) value, sb);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof GuiNode) {
            writeMap(((GuiNode) value).toMap(), sb, indent);
        } else if (value instanceof Map) {
            writeMap((Map<?, ?>) value, sb, indent);
        } else if (value instanceof List) {
            writeList((List<?>) value, sb, indent);
        } else {
            writeString(String.valueOf(value), sb);
        }
    }

    private static void writeNumber(Number n, StringBuilder sb) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append("null");
        } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            sb.append((long) d);
        } else {
            sb.append(d);
        }
    }

    private static void writeMap(Map<?, ?> map, StringBuilder sb, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            newline(sb, indent + 1);
            writeString(String.valueOf(e.getKey()), sb);
            sb.append(": ");
            writePretty(e.getValue(), sb, indent + 1);
            first = false;
        }
        newline(sb, indent);
        sb.append('}');
    }

    private static void writeList(List<?> list, StringBuilder sb, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        boolean first = true;
        for (Object o : list) {
            if (!first) {
                sb.append(',');
            }
            newline(sb, indent + 1);
            writePretty(o, sb, indent + 1);
            first = false;
        }
        newline(sb, indent);
        sb.append(']');
    }

    private static void newline(StringBuilder sb, int indent) {
        sb.append('\n');
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }
}
