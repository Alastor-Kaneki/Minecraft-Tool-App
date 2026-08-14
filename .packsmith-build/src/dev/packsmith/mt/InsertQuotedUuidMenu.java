package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class InsertQuotedUuidMenu extends CompatMenuBase {
 @Override public String name() { return "Insert quoted UUID"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { editor.insertText(editor.getSelectionStart(), "\"" + EditorUtil.uuid() + "\""); }
}
