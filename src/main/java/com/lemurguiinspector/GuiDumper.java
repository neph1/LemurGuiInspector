package com.lemurguiinspector;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Checkbox;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Panel;
import com.simsilica.lemur.PasswordField;
import com.simsilica.lemur.ProgressBar;
import com.simsilica.lemur.RangedValueModel;
import com.simsilica.lemur.RollupPanel;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.TabbedPanel;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.component.ColoredComponent;
import com.simsilica.lemur.component.IconComponent;
import com.simsilica.lemur.component.InsetsComponent;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.component.TextComponent;
import com.simsilica.lemur.core.ComponentStack;
import com.simsilica.lemur.core.GuiComponent;
import com.simsilica.lemur.core.GuiControl;
import com.simsilica.lemur.core.GuiLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a Lemur guiNode scenegraph and produces a sparse {@link GuiNode} tree
 * containing position, size, text, colors and other widget state, suitable
 * for serialization to JSON or a compact outline.
 *
 * <p>Only the effective (post-style) values are read, i.e. what is actually
 * on screen. Non-GUI visuals attached by components (quads, BitmapText) are
 * skipped; they are already described by the component entries.</p>
 */
public class GuiDumper {

    public static class Options {
        /** Include the per-layer component stack (background color, text component, ...). */
        public boolean includeComponents = true;
        /** Include world translation in addition to local position. */
        public boolean includeWorld = true;
        /** Include the flat "leaves" index (clickable/text nodes with pixel rects) in document dumps. */
        public boolean includeLeaves = true;
    }

    public GuiNode dump(Spatial root) {
        return dump(root, new Options());
    }

    public GuiNode dump(Spatial root, Options options) {
        return walk(root, "", options);
    }

    /** Full document: { "root": name, "tree": ..., "leaves": [...] }. */
    public Map<String, Object> dumpDocument(Spatial root, Options options) {
        GuiNode tree = dump(root, options);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("root", root.getName() != null ? root.getName() : root.getClass().getSimpleName());
        doc.put("tree", tree);
        if (options.includeLeaves) {
            List<Object> leaves = new ArrayList<>();
            collectLeaves(tree, leaves);
            doc.put("leaves", leaves);
        }
        return doc;
    }

    public String toJson(Spatial root) {
        return JsonWriter.writePretty(dumpDocument(root, new Options()));
    }

    public String toJson(Spatial root, Options options) {
        return JsonWriter.writePretty(dumpDocument(root, options));
    }

    public String toOutline(Spatial root) {
        return toOutline(dump(root));
    }

    public String toOutline(GuiNode tree) {
        StringBuilder sb = new StringBuilder();
        outline(tree, 0, sb);
        return sb.toString();
    }

    public String diffJson(GuiNode before, GuiNode after) {
        return JsonWriter.writePretty(diff(before, after));
    }

    public List<Map<String, Object>> diff(GuiNode before, GuiNode after) {
        List<Map<String, Object>> out = new ArrayList<>();
        diffNode(before, after, before.path, out);
        return out;
    }

    // ------------------------------------------------------------------

    private GuiNode walk(Spatial s, String path, Options o) {
        GuiNode node = new GuiNode(path);
        node.put("type", s.getClass().getSimpleName());
        if (s.getName() != null) {
            node.put("name", s.getName());
        }
        if (s.getCullHint() == Spatial.CullHint.Never) {
            node.put("visible", false);
        }
        Vector3f scale = s.getLocalScale();
        if (!scale.equals(Vector3f.UNIT_XYZ)) {
            node.put("scale", vec(scale));
        }
        node.put("pos", vec(s.getLocalTranslation()));
        if (o.includeWorld) {
            node.put("worldPos", vec(s.getWorldTranslation()));
        }

        if (s instanceof Panel) {
            Panel p = (Panel) s;
            if (p.getElementId() != null) {
                node.put("elementId", p.getElementId().getId());
            }
            node.put("style", p.getStyle());
        }

        GuiControl control = s.getControl(GuiControl.class);
        if (control != null) {
            Vector3f size = control.getSize();
            if (size != null && (size.x != 0 || size.y != 0)) {
                node.put("size", vec2(size));
            }
            Vector3f pref = control.getPreferredSize();
            if (pref != null && !pref.equals(Vector3f.ZERO) && !pref.equals(size)) {
                node.put("prefSize", vec(pref));
            }
            GuiLayout layout = control.getLayout();
            if (layout != null) {
                node.put("layout", layout.getClass().getSimpleName());
            }
            if (o.includeComponents) {
                List<Object> components = describeComponents(control);
                if (!components.isEmpty()) {
                    node.put("components", components);
                }
            }
            describeWidgetState(s, node);
        }

        if (s instanceof Node) {
            int index = 0;
            for (Spatial child : ((Node) s).getChildren()) {
                if (isGuiNode(child)) {
                    node.children.add(walk(child, path + "/" + index++, o));
                }
            }
        }
        return node;
    }

    /**
     * A child is part of the GUI tree if it is a Lemur widget (Panel / has a
     * GuiControl) or a plain grouping Node that contains such a widget.
     * This skips internal visuals like BitmapText (a Node in jME) and quads.
     */
    private boolean isGuiNode(Spatial s) {
        if (!(s instanceof Node)) {
            return false;
        }
        if (s instanceof Panel || s.getControl(GuiControl.class) != null) {
            return true;
        }
        for (Spatial child : ((Node) s).getChildren()) {
            if (isGuiNode(child)) {
                return true;
            }
        }
        return false;
    }

    private List<Object> describeComponents(GuiControl control) {
        List<Object> result = new ArrayList<>();
        List<GuiComponent> stack = control.getComponents();
        String[] layers = stack instanceof ComponentStack
                ? ((ComponentStack) stack).getLayerOrder() : null;
        if (layers != null) {
            for (String layer : layers) {
                GuiComponent c = control.getComponent(layer);
                if (c != null) {
                    result.add(describeComponent(layer, c));
                }
            }
        } else {
            for (GuiComponent c : stack) {
                result.add(describeComponent(null, c));
            }
        }
        return result;
    }

    private Map<String, Object> describeComponent(String layer, GuiComponent c) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (layer != null) {
            m.put("layer", layer);
        }
        m.put("type", c.getClass().getSimpleName());
        if (c instanceof ColoredComponent) {
            ColorRGBA color = ((ColoredComponent) c).getColor();
            if (color != null) {
                m.put("color", hex(color));
            }
            float alpha = ((ColoredComponent) c).getAlpha();
            if (alpha >= 0 && alpha != 1f) {
                m.put("alpha", num(alpha));
            }
        }
        if (c instanceof QuadBackgroundComponent) {
            QuadBackgroundComponent q = (QuadBackgroundComponent) c;
            Texture t = q.getTexture();
            if (t != null) {
                m.put("texture", t.getName() != null ? t.getName() : String.valueOf(t.getKey()));
            }
            Vector2f margin = q.getMargin();
            if (margin != null && (margin.x != 0 || margin.y != 0)) {
                m.put("margin", vec2(margin));
            }
            if (q.getZOffset() != 0) {
                m.put("zOffset", num(q.getZOffset()));
            }
        }
        if (c instanceof TextComponent) {
            TextComponent t = (TextComponent) c;
            m.put("text", t.getText());
            if (t.getFontSize() != 0) {
                m.put("fontSize", num(t.getFontSize()));
            }
            m.put("hAlign", String.valueOf(t.getHAlignment()));
            m.put("vAlign", String.valueOf(t.getVAlignment()));
        }
        if (c instanceof IconComponent) {
            IconComponent ic = (IconComponent) c;
            Texture t = ic.getImageTexture();
            if (t != null) {
                m.put("icon", t.getName() != null ? t.getName() : String.valueOf(t.getKey()));
            }
            Vector2f iconScale = ic.getIconScale();
            if (iconScale != null && !iconScale.equals(new Vector2f(1, 1))) {
                m.put("iconScale", vec2(iconScale));
            }
        }
        if (c instanceof InsetsComponent) {
            InsetsComponent ic = (InsetsComponent) c;
            Vector3f min = ic.getInsets().getMinInsets();
            Vector3f max = ic.getInsets().getMaxInsets();
            if (!min.equals(Vector3f.ZERO) || !max.equals(Vector3f.ZERO)) {
                m.put("insets", vec(min));
                m.put("insetsMax", vec(max));
            }
        }
        return m;
    }

    private void describeWidgetState(Spatial s, GuiNode node) {
        if (s instanceof Checkbox) {
            Checkbox cb = (Checkbox) s;
            node.put("text", cb.getText());
            node.put("checked", cb.isChecked());
            putColor(node, "color", cb.getColor());
            if (!cb.isEnabled()) {
                node.put("enabled", false);
            }
        } else if (s instanceof Button) {
            Button b = (Button) s;
            node.put("text", b.getText());
            putColor(node, "color", b.getColor());
            if (!b.isEnabled()) {
                node.put("enabled", false);
            }
            if (b.isPressed()) {
                node.put("pressed", true);
            }
        } else if (s instanceof Label) {
            Label l = (Label) s;
            node.put("text", l.getText());
            putColor(node, "color", l.getColor());
            if (l.getFontSize() != 0) {
                node.put("fontSize", num(l.getFontSize()));
            }
            GuiComponent icon = l.getIcon();
            if (icon != null) {
                node.put("icon", icon.getClass().getSimpleName());
            }
        }
        if (s instanceof TextField) {
            node.put("text", ((TextField) s).getText());
            putColor(node, "color", ((TextField) s).getColor());
        }
        if (s instanceof Slider) {
            RangedValueModel model = ((Slider) s).getModel();
            if (model != null) {
                node.put("min", num(model.getMinimum()));
                node.put("max", num(model.getMaximum()));
                node.put("value", num(model.getValue()));
            }
        }
        if (s instanceof ProgressBar) {
            ProgressBar pb = (ProgressBar) s;
            node.put("value", num(pb.getProgressValue()));
            node.put("percent", num(pb.getProgressPercent()));
        }
        if (s instanceof TabbedPanel) {
            TabbedPanel tp = (TabbedPanel) s;
            List<String> tabs = new ArrayList<>();
            int selected = -1;
            TabbedPanel.Tab sel = tp.getSelectedTab();
            int i = 0;
            for (TabbedPanel.Tab tab : tp.getTabs()) {
                tabs.add(tab.getTitle());
                if (tab == sel) {
                    selected = i;
                }
                i++;
            }
            node.put("tabs", tabs);
            if (selected >= 0) {
                node.put("selectedTab", selected);
            }
        }
        if (s instanceof RollupPanel) {
            RollupPanel rp = (RollupPanel) s;
            node.put("title", rp.getTitle());
            node.put("open", rp.isOpen());
        }
        if (s instanceof Container && node.get("layout") == null) {
            // Container always has a layout via GuiControl; nothing extra needed.
        }
    }

    private void collectLeaves(GuiNode node, List<Object> out) {
        Object text = node.get("text");
        String type = (String) node.get("type");
        boolean clickable = "Button".equals(type) || "Checkbox".equals(type)
                || "Slider".equals(type) || "TextField".equals(type)
                || "PasswordField".equals(type);
        if (text != null || clickable) {
            Map<String, Object> leaf = new LinkedHashMap<>();
            leaf.put("path", node.path);
            leaf.put("type", type);
            if (text != null) {
                leaf.put("text", text);
            }
            Object pos = node.get("pos");
            Object size = node.get("size");
            if (pos instanceof List && size instanceof List) {
                List<?> p = (List<?>) pos;
                List<?> sz = (List<?>) size;
                if (p.size() >= 2 && sz.size() >= 2) {
                    leaf.put("rect", List.of(p.get(0), p.get(1), sz.get(0), sz.get(1)));
                }
            }
            out.add(leaf);
        }
        for (GuiNode child : node.children) {
            collectLeaves(child, out);
        }
    }

    private void outline(GuiNode node, int depth, StringBuilder sb) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        Object type = node.get("type");
        sb.append(type != null ? type : "?");
        Object elementId = node.get("elementId");
        if (elementId != null) {
            sb.append('[').append(elementId).append(']');
        }
        Object text = node.get("text");
        if (text != null) {
            sb.append(" \"").append(text).append('"');
        }
        Object title = node.get("title");
        if (title != null) {
            sb.append(" \"").append(title).append('"');
        }
        Object checked = node.get("checked");
        if (checked != null) {
            sb.append(checked.equals(Boolean.TRUE) ? " [x]" : " [ ]");
        }
        Object pos = node.get("pos");
        Object size = node.get("size");
        if (pos instanceof List) {
            List<?> p = (List<?>) pos;
            sb.append(" @").append(p.get(0));
            if (p.size() > 1) {
                sb.append(',').append(p.get(1));
            }
            if (size instanceof List) {
                List<?> sz = (List<?>) size;
                if (sz.size() >= 2) {
                    sb.append(' ').append(sz.get(0)).append('x').append(sz.get(1));
                }
            }
        }
        Object color = node.get("color");
        if (color != null) {
            sb.append(' ').append(color);
        }
        sb.append('\n');
        for (GuiNode child : node.children) {
            outline(child, depth + 1, sb);
        }
    }

    private void diffNode(GuiNode a, GuiNode b, String path, List<Map<String, Object>> out) {
        Map<String, Object> attrsA = a.attrs;
        Map<String, Object> attrsB = b.attrs;
        List<String> keys = new ArrayList<>(attrsA.keySet());
        for (String k : attrsB.keySet()) {
            if (!attrsA.containsKey(k)) {
                keys.add(k);
            }
        }
        for (String k : keys) {
            Object va = attrsA.get(k);
            Object vb = attrsB.get(k);
            if (va == null && vb == null) {
                continue;
            }
            if (va == null || vb == null || !va.equals(vb)) {
                out.add(change(path + "." + k, va, vb));
            }
        }
        int common = Math.min(a.children.size(), b.children.size());
        for (int i = 0; i < common; i++) {
            diffNode(a.children.get(i), b.children.get(i), path + "/" + i, out);
        }
        for (int i = common; i < a.children.size(); i++) {
            out.add(change(path + "/" + i, "present", null));
        }
        for (int i = common; i < b.children.size(); i++) {
            out.add(change(path + "/" + i, null, "present"));
        }
    }

    private Map<String, Object> change(String path, Object before, Object after) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", path);
        m.put("before", before);
        m.put("after", after);
        return m;
    }

    private void putColor(GuiNode node, String key, ColorRGBA color) {
        if (color != null) {
            node.put(key, hex(color));
        }
    }

    static String hex(ColorRGBA c) {
        return String.format("#%02X%02X%02X%02X",
                Math.round(c.r * 255), Math.round(c.g * 255),
                Math.round(c.b * 255), Math.round(c.a * 255));
    }

    static Object num(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return (long) d;
        }
        return Math.round(d * 1000.0) / 1000.0;
    }

    static List<Object> vec(Vector3f v) {
        List<Object> l = new ArrayList<>(3);
        l.add(num(v.x));
        l.add(num(v.y));
        if (v.z != 0) {
            l.add(num(v.z));
        }
        return l;
    }

    static List<Object> vec2(Vector2f v) {
        List<Object> l = new ArrayList<>(2);
        l.add(num(v.x));
        l.add(num(v.y));
        return l;
    }

    static List<Object> vec2(Vector3f v) {
        List<Object> l = new ArrayList<>(2);
        l.add(num(v.x));
        l.add(num(v.y));
        return l;
    }
}
