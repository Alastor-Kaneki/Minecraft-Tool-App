package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class ValidateManifestMenu extends CompatMenuBase {
 @Override public String name() { return "Validate manifest"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { ui.showToast(ManifestUtil.validate(EditorUtil.selectedOrAll(editor))); }
}
