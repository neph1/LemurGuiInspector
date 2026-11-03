package com.lemurguiinspector;

import com.jme3.app.Application;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.scene.Node;
import com.simsilica.lemur.event.BaseAppState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example in-game hook: attach to the state manager with the GUI root node,
 * then press F12 to write a JSON dump and a text outline of the current
 * scenegraph to disk. Handy for agent-driven GUI debugging sessions.
 *
 * <pre>
 *   stateManager.attach(new GuiDumpState(guiNode, Paths.get("gui-dumps")));
 * </pre>
 */
public class GuiDumpState extends BaseAppState {

    public static final String MAPPING_DUMP = "lemurGuiInspect";

    private final Node guiNode;
    private final Path outDir;
    private final GuiDumper dumper = new GuiDumper();
    private int counter = 0;

    public GuiDumpState(Node guiNode) {
        this(guiNode, Paths.get("gui-dumps"));
    }

    public GuiDumpState(Node guiNode, Path outDir) {
        this.guiNode = guiNode;
        this.outDir = outDir;
    }

    @Override
    protected void initialize(Application app) {
    }

    @Override
    protected void cleanup(Application app) {
        getApplication().getInputManager().deleteMapping(MAPPING_DUMP);
    }

    @Override
    protected void enable() {
        Application app = getApplication();
        app.getInputManager().addMapping(MAPPING_DUMP, new KeyTrigger(KeyInput.KEY_F12));
        app.getInputManager().addListener((ActionListener) (name, isPressed, tpf) -> {
            if (isPressed && MAPPING_DUMP.equals(name)) {
                dumpNow();
            }
        }, MAPPING_DUMP);
    }

    @Override
    protected void disable() {
        Application app = getApplication();
        if (app != null) {
            app.getInputManager().deleteMapping(MAPPING_DUMP);
        }
    }

    /** Dumps immediately; returns the base path of the written files (without extension). */
    public Path dumpNow() {
        try {
            Files.createDirectories(outDir);
            Path base = outDir.resolve("gui-dump-" + (counter++));
            Files.write(base.resolveSibling(base.getFileName() + ".json"),
                    dumper.toJson(guiNode).getBytes(StandardCharsets.UTF_8));
            Files.write(base.resolveSibling(base.getFileName() + ".txt"),
                    dumper.toOutline(guiNode).getBytes(StandardCharsets.UTF_8));
            return base;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
