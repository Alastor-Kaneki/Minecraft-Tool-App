package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class GenerateBehaviorPackMenu extends CompatMenuBase {
 @Override public String name() { return "Generate BP"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { EditorUtil.replaceWhole(editor, ManifestUtil.bp()); ui.showToast("Behavior manifest generated"); }
}
