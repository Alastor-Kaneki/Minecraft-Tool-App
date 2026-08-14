package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class RefreshManifestUuidsMenu extends CompatMenuBase {
 @Override public String name() { return "Refresh manifest UUIDs"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { EditorUtil.replaceSelectionOrAll(editor, ManifestUtil.refreshHeaderAndModules(EditorUtil.selectedOrAll(editor))); ui.showToast("Header/module UUIDs refreshed"); }
}
