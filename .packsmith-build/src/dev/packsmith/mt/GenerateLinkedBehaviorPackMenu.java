package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class GenerateLinkedBehaviorPackMenu extends CompatMenuBase {
 @Override public String name() { return "Generate linked BP"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { EditorUtil.replaceWhole(editor, ManifestUtil.linkedBp()); ui.showToast("Linked BP template generated; replace the RP UUID placeholder"); }
}
