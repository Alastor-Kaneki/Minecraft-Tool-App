package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class MinifyJsonMenu extends CompatMenuBase {
 @Override public String name() { return "Minify JSON"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { try { EditorUtil.replaceSelectionOrAll(editor, JsonUtil.minify(EditorUtil.selectedOrAll(editor))); ui.showToast("JSON minified"); } catch (Throwable e) { ui.showToast("Invalid JSON: " + e.getMessage()); } }
}
