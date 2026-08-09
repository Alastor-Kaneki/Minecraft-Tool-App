package dev.alastor.bedrockforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(169, 0, 255);
    private static final int PANEL = Color.rgb(18, 18, 18);
    private static final String MC_ROOT = "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/minecraftWorlds";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> worlds = new ArrayList<>();

    private TextView status;
    private Spinner worldSpinner;
    private LinearLayout slots;
    private Button saveButton, syncButton;
    private InventoryStore.Section section = InventoryStore.Section.INVENTORY;
    private InventoryStore store;
    private String currentWorld;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        immersive();
        setContentView(buildUi());
        updateShizukuStatus();
        if (ShizukuBridge.granted()) refreshWorlds();
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        if (status != null) updateShizukuStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("Bedrock Forge", 28, Color.WHITE);
        title.setTypeface(title.getTypeface(), 1);
        root.addView(title);
        TextView subtitle = text("Offline Minecraft Bedrock inventory editor", 14, Color.LTGRAY);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, 12));

        status = text("Checking Shizuku…", 14, Color.LTGRAY);
        status.setBackgroundColor(PANEL);
        status.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(status, lp(-1, -2, 0, 0, 0, 10));

        LinearLayout access = row();
        Button connect = button("Connect Shizuku");
        connect.setOnClickListener(v -> {
            if (!ShizukuBridge.running()) {
                toast("Start Shizuku first, then return here.");
            } else if (!ShizukuBridge.granted()) {
                ShizukuBridge.requestPermission(5287);
            } else {
                refreshWorlds();
            }
        });
        Button refresh = button("Refresh Worlds");
        refresh.setOnClickListener(v -> refreshWorlds());
        access.addView(connect, weighted());
        access.addView(refresh, weighted());
        root.addView(access, lp(-1, -2, 0, 0, 0, 12));

        root.addView(sectionLabel("Minecraft world"));
        worldSpinner = new Spinner(this);
        worldSpinner.setBackgroundTintList(ColorStateList.valueOf(PURPLE));
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, worlds) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView v=(TextView)super.getView(position,convertView,parent); v.setTextColor(Color.WHITE); v.setPadding(dp(8),dp(10),dp(8),dp(10)); return v;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v=(TextView)super.getDropDownView(position,convertView,parent); v.setTextColor(Color.WHITE); v.setBackgroundColor(PANEL); return v;
            }
        };
        worldSpinner.setAdapter(adapter);
        root.addView(worldSpinner, lp(-1, -2, 0, 0, 0, 8));

        LinearLayout importRow = row();
        Button importButton = button("Import / Update");
        importButton.setOnClickListener(v -> importSelectedWorld());
        Button openButton = button("Open Mirror");
        openButton.setOnClickListener(v -> openSelectedMirror());
        importRow.addView(importButton, weighted());
        importRow.addView(openButton, weighted());
        root.addView(importRow, lp(-1, -2, 0, 0, 0, 16));

        root.addView(sectionLabel("Inventory editor"));
        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        LinearLayout tabs = row();
        tabs.setPadding(0,0,0,dp(8));
        addTab(tabs, "Inventory", InventoryStore.Section.INVENTORY);
        addTab(tabs, "Armor", InventoryStore.Section.ARMOR);
        addTab(tabs, "Offhand", InventoryStore.Section.OFFHAND);
        addTab(tabs, "Ender", InventoryStore.Section.ENDER);
        tabsScroll.addView(tabs);
        root.addView(tabsScroll, new LinearLayout.LayoutParams(-1,-2));

        slots = new LinearLayout(this);
        slots.setOrientation(LinearLayout.VERTICAL);
        slots.setBackgroundColor(PANEL);
        slots.setPadding(dp(8),dp(8),dp(8),dp(8));
        TextView empty=text("Import or open a mirror to edit inventory.",14,Color.LTGRAY);
        empty.setPadding(dp(8),dp(12),dp(8),dp(12));
        slots.addView(empty);
        root.addView(slots, lp(-1, -2, 0, 0, 0, 12));

        saveButton = button("Save Mirror");
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(v -> saveMirror());
        syncButton = button("Sync to Minecraft");
        syncButton.setEnabled(false);
        syncButton.setOnClickListener(v -> confirmSync());
        root.addView(saveButton, lp(-1,-2,0,0,0,8));
        root.addView(syncButton, lp(-1,-2,0,0,0,0));

        TextView warning=text("Minecraft must be completely closed while importing or syncing. Every Save Mirror operation backs up the raw ~local_player record first.",12,Color.GRAY);
        root.addView(warning, lp(-1,-2,0,10,0,0));
        return scroll;
    }

    private void addTab(LinearLayout tabs, String label, InventoryStore.Section target) {
        Button b=button(label);
        b.setOnClickListener(v -> { section=target; renderSlots(); });
        tabs.addView(b, new LinearLayout.LayoutParams(dp(118), dp(48)));
    }

    private void updateShizukuStatus() {
        if (!ShizukuBridge.running()) status.setText("Shizuku is not running. Offline mirrors remain available.");
        else if (!ShizukuBridge.granted()) status.setText("Shizuku is running · permission required");
        else status.setText("Shizuku connected · ready for Minecraft access");
    }

    private void refreshWorlds() {
        run("Discovering Minecraft worlds…", () -> {
            String cmd = "ROOT=" + ShizukuBridge.quote(MC_ROOT) + "; " +
                    "[ -d \"$ROOT\" ] || { echo __BF_MISSING__; exit 0; }; " +
                    "for d in \"$ROOT\"/*; do [ -d \"$d\" ] && basename \"$d\"; done";
            return ShizukuBridge.exec(cmd);
        }, output -> {
            worlds.clear();
            for (String line : output.split("\\R")) {
                String s=line.trim(); if(!s.isEmpty() && !s.equals("__BF_MISSING__")) worlds.add(s);
            }
            Collections.sort(worlds);
            ((ArrayAdapter<?>)worldSpinner.getAdapter()).notifyDataSetChanged();
            status.setText(output.contains("__BF_MISSING__") ? "Minecraft worlds directory is not accessible." :
                    "Shizuku connected · " + worlds.size() + " Minecraft world(s) discovered");
        });
    }

    private String selectedWorld() {
        Object selected=worldSpinner.getSelectedItem();
        return selected==null?null:selected.toString();
    }

    private File mirrorFor(String world) {
        return new File(new File(new File(getFilesDir(), "worlds"), safe(world)), ".tmp");
    }

    private void importSelectedWorld() {
        String world=selectedWorld();
        if(world==null){ toast("No Minecraft world selected."); return; }
        closeStore();
        File mirror=mirrorFor(world);
        if(!mirror.exists() && !mirror.mkdirs()){ toast("Could not create private mirror folder."); return; }
        String source=MC_ROOT+"/"+world;
        String inner="mkdir -p " + ShizukuBridge.quote(mirror.getAbsolutePath()) +
                " && cd " + ShizukuBridge.quote(mirror.getAbsolutePath()) +
                " && rm -rf db level.dat && tar -xf -";
        String cmd="cd " + ShizukuBridge.quote(source) +
                " && [ -f level.dat ] && [ -d db ] && tar -cf - level.dat db" +
                " | run-as dev.alastor.bedrockforge sh -c " + ShizukuBridge.quote(inner);
        run("Importing " + world + "…", () -> ShizukuBridge.exec(cmd), ignored -> openMirror(world));
    }

    private void openSelectedMirror() {
        String world=selectedWorld();
        if(world==null){ toast("No world selected."); return; }
        openMirror(world);
    }

    private void openMirror(String world) {
        closeStore();
        File mirror=mirrorFor(world);
        run("Opening offline mirror…", () -> new InventoryStore(mirror, new File(getFilesDir(),"backups"), world), opened -> {
            store=opened; currentWorld=world; saveButton.setEnabled(true); syncButton.setEnabled(true);
            status.setText("Editing offline mirror · " + world);
            renderSlots();
        });
    }

    private void renderSlots() {
        slots.removeAllViews();
        if(store==null){ slots.addView(text("Import or open a mirror to edit inventory.",14,Color.LTGRAY)); return; }
        try {
            for(InventoryStore.Item item:store.items(section)) {
                Button b=button(item.display());
                b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                b.setAllCaps(false);
                b.setOnClickListener(v -> editItem(item));
                slots.addView(b, lp(-1,dp(52),0,0,0,4));
            }
        } catch(Throwable t){ showError("Could not render " + section.nbtName, t); }
    }

    private void editItem(InventoryStore.Item item) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(4),dp(20),0);
        EditText name=field("Item name", item.name, false);
        EditText count=field("Count", String.valueOf(item.count), true);
        EditText damage=field("Damage", String.valueOf(item.damage), true);
        EditText ench=field("Enchantments: id:lvl,id:lvl", item.enchantments, false);
        box.addView(name); box.addView(count); box.addView(damage); box.addView(ench);
        new AlertDialog.Builder(this)
                .setTitle(section.nbtName + " slot " + item.slot)
                .setView(box)
                .setNeutralButton("Clear", (d,w) -> {
                    try { store.update(section,new InventoryStore.Item(item.slot,"",0,0,"")); status.setText("Pending mirror changes · tap Save Mirror"); renderSlots(); }
                    catch(Throwable t){ showError("Could not clear slot",t); }
                })
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Apply", (d,w) -> {
                    try {
                        InventoryStore.Item changed=new InventoryStore.Item(item.slot,name.getText().toString(),parseInt(count,1),parseInt(damage,0),ench.getText().toString());
                        store.update(section,changed); status.setText("Pending mirror changes · tap Save Mirror"); renderSlots();
                    } catch(Throwable t){ showError("Invalid item",t); }
                }).show();
    }

    private void saveMirror() {
        if(store==null)return;
        InventoryStore target=store;
        run("Saving ~local_player…", target::save, backup -> {
            status.setText("Mirror saved · backup: " + backup.getName()); renderSlots();
        });
    }

    private void confirmSync() {
        if(store==null||currentWorld==null)return;
        new AlertDialog.Builder(this)
                .setTitle("Sync to Minecraft?")
                .setMessage("Minecraft must be fully closed. Bedrock Forge will save the mirror, close LevelDB, then replace level.dat and db/ in the selected world.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Sync",(d,w)->syncNow()).show();
    }

    private void syncNow() {
        if(store==null||currentWorld==null)return;
        final String world=currentWorld;
        final InventoryStore target=store;
        final File mirror=mirrorFor(world);
        store=null; saveButton.setEnabled(false); syncButton.setEnabled(false);
        run("Saving and syncing " + world + "…", () -> {
            target.save(); target.close();
            String source=MC_ROOT+"/"+world;
            String inner="cd " + ShizukuBridge.quote(mirror.getAbsolutePath()) + " && tar -cf - level.dat db";
            String cmd="run-as dev.alastor.bedrockforge sh -c " + ShizukuBridge.quote(inner) +
                    " | (cd " + ShizukuBridge.quote(source) + " && rm -rf db level.dat && tar -xf -)";
            return ShizukuBridge.exec(cmd);
        }, ignored -> {
            status.setText("Sync complete · " + world);
            openMirror(world);
        });
    }

    private <T> void run(String message, Callable<T> task, Consumer<T> success) {
        status.setText(message);
        worker.submit(() -> {
            try { T result=task.call(); main.post(() -> success.accept(result)); }
            catch(Throwable t){ main.post(() -> showError(message,t)); }
        });
    }

    private void closeStore() {
        InventoryStore old=store; store=null; currentWorld=null;
        saveButtonSafe(false); syncButtonSafe(false);
        if(old!=null) worker.submit(() -> { try{ old.close(); }catch(Throwable ignored){} });
    }

    private void saveButtonSafe(boolean enabled){ if(saveButton!=null)saveButton.setEnabled(enabled); }
    private void syncButtonSafe(boolean enabled){ if(syncButton!=null)syncButton.setEnabled(enabled); }

    private void showError(String context, Throwable t) {
        String msg=t.getMessage(); if(msg==null||msg.trim().isEmpty())msg=t.getClass().getSimpleName();
        status.setText("Error · " + msg);
        new AlertDialog.Builder(this).setTitle(context).setMessage(msg).setPositiveButton("OK",null).show();
    }

    private EditText field(String hint,String value,boolean numeric) {
        EditText e=new EditText(this); e.setHint(hint); e.setHintTextColor(Color.GRAY); e.setTextColor(Color.WHITE); e.setText(value==null?"":value);
        e.setSingleLine(true); e.setPadding(0,dp(8),0,dp(8));
        if(numeric)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        return e;
    }
    private int parseInt(EditText e,int fallback){ String s=e.getText().toString().trim(); return s.isEmpty()?fallback:Integer.parseInt(s); }

    private TextView sectionLabel(String value) { TextView t=text(value.toUpperCase(Locale.US),12,PURPLE); t.setTypeface(t.getTypeface(),1); return t; }
    private TextView text(String value,int sp,int color){ TextView t=new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); return t; }
    private LinearLayout row(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private Button button(String value){ Button b=new Button(this); b.setText(value); b.setTextColor(Color.WHITE); b.setBackgroundTintList(ColorStateList.valueOf(PURPLE)); return b; }
    private LinearLayout.LayoutParams weighted(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1); p.setMargins(dp(2),0,dp(2),0); return p; }
    private LinearLayout.LayoutParams lp(int w,int h,int l,int t,int r,int b){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private String safe(String s){ return s.replaceAll("[^A-Za-z0-9._=-]","_"); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void immersive(){ getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK); getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }

    @Override protected void onDestroy(){ super.onDestroy(); InventoryStore old=store; store=null; if(old!=null)try{old.close();}catch(Throwable ignored){} worker.shutdownNow(); }
}
