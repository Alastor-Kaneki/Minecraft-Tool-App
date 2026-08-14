package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class GenerateResourcePackMenu extends CompatMenuBase {
 @Override public String name() { return "Generate RP"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { EditorUtil.replaceWhole(editor, ManifestUtil.rp()); ui.showToast("Resource manifest generated"); }
}
