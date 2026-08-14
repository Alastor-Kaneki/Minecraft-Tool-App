package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class FormatJsonMenu extends CompatMenuBase {
 @Override public String name() { return "Format JSON"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { try { EditorUtil.replaceSelectionOrAll(editor, JsonUtil.pretty(EditorUtil.selectedOrAll(editor))); ui.showToast("JSON formatted"); } catch (Throwable e) { ui.showToast("Invalid JSON: " + e.getMessage()); } }
}
