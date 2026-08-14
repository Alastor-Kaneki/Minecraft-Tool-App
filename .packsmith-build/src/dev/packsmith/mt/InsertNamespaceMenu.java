package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class InsertNamespaceMenu extends CompatMenuBase {
 @Override public String name() { return "Insert namespace"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { editor.insertText(editor.getSelectionStart(), "packsmith:"); }
}
