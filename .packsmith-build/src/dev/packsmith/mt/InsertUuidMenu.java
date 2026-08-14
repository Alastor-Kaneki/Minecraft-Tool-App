package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class InsertUuidMenu extends CompatMenuBase {
 @Override public String name() { return "Insert UUID"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { editor.insertText(editor.getSelectionStart(), EditorUtil.uuid()); }
}
