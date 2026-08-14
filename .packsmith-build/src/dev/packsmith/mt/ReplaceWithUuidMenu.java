package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class ReplaceWithUuidMenu extends CompatMenuBase {
 @Override public String name() { return "Replace with UUID"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { int a=editor.getSelectionStart(),b=editor.getSelectionEnd(); if(a==b) editor.insertText(a,EditorUtil.uuid()); else editor.replaceText(Math.min(a,b),Math.max(a,b),EditorUtil.uuid()); }
}
