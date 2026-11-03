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
import com.simsilica.lemur.FillMode;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.component.BoxLayout;
import com.simsilica.lemur.core.GuiControl;

import java.lang.reflect.Proxy;

public class DumpDemo {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        JmeSystem.setSystemDelegate(new JmeSystemDelegate() {
            @Override public void writeImageFile(java.io.OutputStream out, String ext, java.nio.ByteBuffer data, int w, int h) { }
            @Override public java.net.URL getPlatformAssetConfigURL() { return JmeSystemDelegate.class.getResource("/com/jme3/asset/Desktop.cfg"); }
            @Override public JmeContext newContext(com.jme3.system.AppSettings s, JmeContext.Type t) { return null; }
            @Override public com.jme3.audio.AudioRenderer newAudioRenderer(com.jme3.system.AppSettings s) { return null; }
            @Override public void initialize(com.jme3.system.AppSettings s) { }
            @Override public void showSoftKeyboard(boolean show) { }
        });
        AssetManager assets = new DesktopAssetManager(true);
        assets.registerLoader(com.jme3.texture.plugins.AWTLoader.class, "png", "jpg", "jpeg", "bmp", "gif");
        assets.registerLocator(null, ClasspathLocator.class);
        ClassLoader cl = DumpDemo.class.getClassLoader();
        JmeContext ctx = (JmeContext) Proxy.newProxyInstance(cl, new Class<?>[]{JmeContext.class},
                (p, m, a) -> m.getName().equals("getType") ? JmeContext.Type.Headless
                        : m.getName().equals("getAssetManager") ? assets : null);
        Application app = (Application) Proxy.newProxyInstance(cl, new Class<?>[]{Application.class},
                (p, m, a) -> m.getName().equals("getAssetManager") ? assets
                        : m.getName().equals("getContext") ? ctx : null);
        GuiGlobals.initialize(app);

        Node guiNode = new Node("guiNode");
        Container main = new Container(new BoxLayout(Axis.Y, FillMode.None));
        main.setName("mainPanel");
        main.addChild(new Label("Quest Log"));
        main.addChild(new Button("OK"));
        DefaultCheckboxModel model = new DefaultCheckboxModel();
        model.setChecked(true);
        main.addChild(new Checkbox("Enabled", model));
        guiNode.attachChild(main);
        main.getControl(GuiControl.class).setSize(new Vector3f(400, 300, 0));
        guiNode.updateLogicalState(0f);
        guiNode.updateGeometricState();

        GuiDumper dumper = new GuiDumper();
        System.out.println("===== OUTLINE =====");
        System.out.print(dumper.toOutline(guiNode));
        System.out.println("===== JSON =====");
        System.out.println(dumper.toJson(guiNode));
    }
}
