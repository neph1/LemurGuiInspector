# lemur-gui-inspector

Dumps a [Lemur](https://github.com/jMonkeyEngine-Contributions/Lemur) (jMonkeyEngine) GUI
scenegraph — the `guiNode` tree — into a readable, agent-friendly format so an LLM agent
(or a human) can debug and iterate on a GUI: position, size, text, colors, widget state,
plus a flat click-target index and structural diffs between two snapshots.

## Output formats

- **JSON** (canonical): sparse — defaults are omitted, integral numbers print without
  decimals, colors as `#RRGGBBAA`. Contains the full `tree` plus a flat `leaves` index
  of every node with text or a click target, each with a pixel `rect` (`[x, y, w, h]` in
  guiNode-local coordinates) so an agent can locate e.g. "the OK button" and click it.
- **Outline** (derived, one line per node): quick human/agent scan.
- **Diff**: structural JSON diff of two dumps (`before`/`after` per changed attribute and
  added/removed subtrees) — the "what changed after this action" view.

Example outline:

```text
Node @0,0
  Container[container] @0,0 87.01x71
    Label[label] "Quest Log" @0,0 87.01x21 #FFFFFFFF
    Button[button] "OK" @0,-21 87.01x22 #FFFFFFFF
    Checkbox[checkbox] "Enabled" [x] @0,-43 87.01x28 #FFFFFFFF
```

Example JSON (abridged):

```json
{
  "root": "guiNode",
  "tree": {
    "type": "Node", "name": "guiNode", "pos": [0, 0],
    "children": [
      {
        "type": "Container", "elementId": "container", "layout": "BoxLayout",
        "pos": [0, 0], "size": [87.01, 71],
        "children": [
          { "type": "Button", "elementId": "button", "text": "OK",
            "pos": [0, -21], "size": [87.01, 22], "color": "#FFFFFFFF",
            "components": [ { "layer": "text", "type": "TextComponent",
                              "text": "OK", "color": "#FFFFFFFF", "fontSize": 17 } ] }
        ]
      }
    ]
  },
  "leaves": [ { "path": "/0/1", "type": "Button", "text": "OK", "rect": [0, -21, 87.01, 22] } ]
}
```

## What is captured per node

- `type`, `name`, `elementId`, `style` (Lemur style identity)
- `pos` (local translation, top-left/y-down convention), `worldPos`, `size`, `prefSize`,
  `visible`, `scale`
- `layout` for containers
- `components`: per-layer component stack — background/border/insets/text/icon with
  color (`#RRGGBBAA`), alpha, texture, margins, insets, font size, alignment
- widget state: `text`/`color`/`fontSize` (Label, Button, TextField), `checked` (Checkbox),
  `enabled`/`pressed` (Button), `min`/`max`/`value` (Slider), `value`/`percent`
  (ProgressBar), `tabs`/`selectedTab` (TabbedPanel), `title`/`open` (RollupPanel)

Only effective (post-style) values are read. Internal visuals (BitmapText, quads) are
skipped; they are described by their component entries.

## Usage

In a running jME3 app, attach the dump state and press **F12** to write
`gui-dumps/gui-dump-N.json` and `gui-dumps/gui-dump-N.txt`:

```java
GuiGlobals.initialize(this);
Node guiNode = new Node("guiNode");
guiNode.setLocalScale(...); // your usual GUI scaling
stateManager.attach(new GuiDumpState(guiNode, Paths.get("gui-dumps")));
```

Programmatic use (e.g. from a test or a debug command):

```java
GuiDumper dumper = new GuiDumper();
String json    = dumper.toJson(guiNode);          // full document incl. leaves index
String outline = dumper.toOutline(guiNode);       // one line per node
GuiNode before = dumper.dump(guiNode);
// ... interact with the GUI ...
String diff    = dumper.diffJson(before, dumper.dump(guiNode));
```

Options (`GuiDumper.Options`): toggle `includeComponents`, `includeWorld`, `includeLeaves`.

## Build & test

Standard Maven build (deps: `jme3-core` 3.8.1-stable, `lemur` 1.16.0, JUnit 5):

```bash
mvn test
```

The tests run headless: they initialize `GuiGlobals` with a minimal `AssetManager`
(classpath locator + jme3-desktop `AWTLoader` for the bundled default font) via a dynamic
proxy `Application`, build a real `Container` tree, run the layout pass, and verify the
dump/outline/leaves/diff output. `DumpDemo` (in test sources) prints a full sample dump.

## Layout

```
src/main/java/com/lemurguiinspector/
  GuiNode.java        sparse tree node (attrs + children)
  GuiDumper.java      scenegraph walker + leaves index + diff
  JsonWriter.java     dependency-free JSON writer
  GuiDumpState.java   F12 hotkey AppState writing .json/.txt dumps
src/test/java/com/lemurguiinspector/
  GuiDumperTest.java  headless tests
  DumpDemo.java       prints a sample dump
```
