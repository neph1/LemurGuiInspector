package com.lemurguiinspector;

import com.jme3.app.Application;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.plugins.ClasspathLocator;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.system.JmeContext;
import com.jme3.system.JmeSystem;
import com.jme3.system.JmeSystemDelegate;
import com.simsilica.lemur.Axis;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Checkbox;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.DefaultCheckboxModel;
import com.simsilica.lemur.DefaultRangedValueModel;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.GuiControl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiDumperTest {

    @BeforeAll
    static void initGuiGlobals() {
        if (GuiGlobals.getInstance() != null) {
            return;
        }
        JmeSystem.setSystemDelegate(new JmeSystemDelegate() {
            @Override
            public void writeImageFile(java.io.OutputStream out, String ext, java.nio.ByteBuffer data,
                                       int width, int height) {
                throw new UnsupportedOperationException("headless test");
            }

            @Override
            public java.net.URL getPlatformAssetConfigURL() {
                return JmeSystemDelegate.class.getResource("/com/jme3/asset/Desktop.cfg");
            }

            @Override
            public JmeContext newContext(com.jme3.system.AppSettings settings, JmeContext.Type contextType) {
                throw new UnsupportedOperationException("headless test");
            }

            @Override
            public com.jme3.audio.AudioRenderer newAudioRenderer(com.jme3.system.AppSettings settings) {
                throw new UnsupportedOperationException("headless test");
            }

            @Override
            public void initialize(com.jme3.system.AppSettings settings) {
            }

            @Override
            public void showSoftKeyboard(boolean show) {
            }
        });
        System.setProperty("java.awt.headless", "true");
        AssetManager assets = new DesktopAssetManager(true);
        assets.registerLoader(com.jme3.texture.plugins.AWTLoader.class, "png", "jpg", "jpeg", "bmp", "gif");
        assets.registerLocator(null, ClasspathLocator.class);
        ClassLoader cl = GuiDumperTest.class.getClassLoader();
        JmeContext ctx = (JmeContext) Proxy.newProxyInstance(cl, new Class<?>[]{JmeContext.class},
                (p, m, a) -> {
                    if (m.getName().equals("getType")) {
                        return JmeContext.Type.Headless;
                    }
                    if (m.getName().equals("getAssetManager")) {
                        return assets;
                    }
                    return defaultValue(m.getReturnType());
                });
        Application app = (Application) Proxy.newProxyInstance(cl, new Class<?>[]{Application.class},
                (p, m, a) -> {
                    if (m.getName().equals("getAssetManager")) {
                        return assets;
                    }
                    if (m.getName().equals("getContext")) {
                        return ctx;
                    }
                    return defaultValue(m.getReturnType());
                });
        GuiGlobals.initialize(app);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private Node buildSampleGui(Container[] mainOut, Button[] buttonOut) {
        Node guiNode = new Node("guiNode");
        Container main = new Container(new BoxLayout(Axis.Y, FillMode.None));
        main.setName("mainPanel");

        Label title = new Label("Quest Log");
        Button ok = new Button("OK");
        DefaultCheckboxModel model = new DefaultCheckboxModel();
        model.setChecked(true);
        Checkbox enabled = new Checkbox("Enabled", model);
        DefaultRangedValueModel sliderModel = new DefaultRangedValueModel();
        sliderModel.setMinimum(0);
        sliderModel.setMaximum(100);
        sliderModel.setValue(25);
        Slider slider = new Slider(sliderModel);
        TextField filter = new TextField("filter...");

        main.addChild(title);
        main.addChild(ok);
        main.addChild(enabled);
        main.addChild(slider);
        main.addChild(filter);
        guiNode.attachChild(main);

        main.getControl(GuiControl.class).setSize(new Vector3f(400, 300, 0));
        guiNode.updateLogicalState(0f);
        guiNode.updateGeometricState();

        mainOut[0] = main;
        buttonOut[0] = ok;
        return guiNode;
    }

    @Test
    void dumpsTreeWithPositionsTextAndColors() {
        Container[] main = new Container[1];
        Button[] ok = new Button[1];
        Node guiNode = buildSampleGui(main, ok);

        GuiDumper dumper = new GuiDumper();
        String json = dumper.toJson(guiNode);

        assertTrue(json.contains("\"root\": \"guiNode\""), json);
        assertTrue(json.contains("\"type\": \"Container\""), json);
        assertTrue(json.contains("\"elementId\": \"container\""), json);
        assertTrue(json.contains("\"layout\": \"BoxLayout\""), json);
        assertTrue(json.contains("\"text\": \"OK\""), json);
        assertTrue(json.contains("\"text\": \"Quest Log\""), json);
        assertTrue(json.contains("\"checked\": true"), json);
        assertTrue(json.contains("\"value\": 25"), json);
        assertTrue(json.contains("\"text\": \"filter...\""), json);
        assertTrue(json.contains("\"color\": \"#"), json);
        assertTrue(json.contains("\"components\""), json);
        assertTrue(json.contains("QuadBackgroundComponent"), json);
        assertTrue(json.contains("TextComponent"), json);

        // The button should have been laid out by the BoxLayout: non-zero pos and a size.
        GuiNode tree = dumper.dump(guiNode);
        GuiNode buttonNode = findByText(tree, "OK");
        assertTrue(buttonNode != null, "button node found");
        List<?> pos = (List<?>) buttonNode.get("pos");
        assertTrue(pos != null && pos.size() >= 2, "button has pos: " + pos);
        assertTrue(((Number) pos.get(0)).doubleValue() != 0 || ((Number) pos.get(1)).doubleValue() != 0,
                "button laid out away from origin, pos=" + pos);
        assertTrue(buttonNode.get("size") != null, "button has size");
    }

    @Test
    void outlineIsOneLinePerNode() {
        Container[] main = new Container[1];
        Button[] ok = new Button[1];
        Node guiNode = buildSampleGui(main, ok);

        String outline = new GuiDumper().toOutline(guiNode);
        String[] lines = outline.split("\n");
        // root + container + 5 widgets (+ internal child nodes of composite widgets)
        assertTrue(lines.length >= 7, outline);
        assertTrue(lines[0].startsWith("Node @"), outline);
        assertTrue(lines[1].contains("Container[container]"), outline);
        assertTrue(outline.contains("Button[button] \"OK\""), outline);
        assertTrue(outline.contains("[x]"), outline);
    }

    @Test
    @SuppressWarnings("unchecked")
    void leavesIndexContainsClickableWithRect() {
        Container[] main = new Container[1];
        Button[] ok = new Button[1];
        Node guiNode = buildSampleGui(main, ok);

        GuiDumper dumper = new GuiDumper();
        Map<String, Object> doc = dumper.dumpDocument(guiNode, new GuiDumper.Options());
        List<Object> leaves = (List<Object>) doc.get("leaves");
        assertTrue(leaves != null && !leaves.isEmpty());
        Map<String, Object> okLeaf = null;
        for (Object o : leaves) {
            Map<String, Object> leaf = (Map<String, Object>) o;
            if ("OK".equals(leaf.get("text"))) {
                okLeaf = leaf;
            }
        }
        assertTrue(okLeaf != null, "leaf with text OK: " + leaves);
        assertTrue(okLeaf.get("path") != null);
        assertTrue(okLeaf.get("rect") instanceof List, "leaf has rect: " + okLeaf);
    }

    @Test
    void diffDetectsTextChange() {
        Container[] main = new Container[1];
        Button[] ok = new Button[1];
        Node guiNode = buildSampleGui(main, ok);

        GuiDumper dumper = new GuiDumper();
        GuiNode before = dumper.dump(guiNode);
        ok[0].setText("Cancel");
        GuiNode after = dumper.dump(guiNode);

        String diffJson = dumper.diffJson(before, after);
        assertTrue(diffJson.contains("\"before\": \"OK\""), diffJson);
        assertTrue(diffJson.contains("\"after\": \"Cancel\""), diffJson);
    }

    private GuiNode findByText(GuiNode node, String text) {
        if (text.equals(node.get("text"))) {
            return node;
        }
        for (GuiNode child : node.children) {
            GuiNode found = findByText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
