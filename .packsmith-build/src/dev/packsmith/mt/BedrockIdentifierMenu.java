package dev.packsmith.mt;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.ui.PluginUI;
public class BedrockIdentifierMenu extends CompatMenuBase {
 @Override public String name() { return "Bedrock identifier"; }
 @Override public void onMenuClick(PluginUI ui, TextEditor editor) { int a=editor.getSelectionStart(),b=editor.getSelectionEnd(); if(a==b) editor.insertText(a,"packsmith:"); else {String s=editor.subText(Math.min(a,b),Math.max(a,b)); String id=EditorUtil.normalizeId(s); if(id.indexOf(':')<0) id="packsmith:"+id; editor.replaceText(Math.min(a,b),Math.max(a,b),id);} }
}
