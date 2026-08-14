package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class ValidateJsonMenu extends CompatMenuBase {
 @Override public String name() { return "Validate JSON"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { try { JsonUtil.validate(EditorUtil.selectedOrAll(editor)); ui.showToast("Valid JSON"); } catch (Throwable e) { ui.showToast("Invalid JSON: " + e.getMessage()); } }
}
