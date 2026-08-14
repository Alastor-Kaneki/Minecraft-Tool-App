package dev.packsmith.mt;

import android.graphics.drawable.Drawable;
import bin.mt.plugin.api.PluginContext;
import bin.mt.plugin.api.drawable.MaterialIcons;
import bin.mt.plugin.api.editor.TextEditor;
import bin.mt.plugin.api.editor.TextEditorToolMenu;
import bin.mt.plugin.api.ui.PluginUI;

public abstract class CompatMenuBase implements TextEditorToolMenu {
    private PluginContext context;
    @Override public void init(PluginContext context) { this.context = context; }
    @Override public PluginContext getContext() { return context; }
    @Override public boolean isEnabled() { return true; }
    @Override public Drawable icon() { return MaterialIcons.get("data_object"); }
    @Override public void onPluginButtonClick(PluginUI ui) { ui.showToast("PackSmith compatibility build"); }
    @Override public boolean checkVisible(TextEditor editor) { return !editor.isReadOnly(); }
}
