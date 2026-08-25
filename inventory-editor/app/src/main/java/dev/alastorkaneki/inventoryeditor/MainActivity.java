package dev.alastorkaneki.inventoryeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.alastorkaneki.inventoryeditor.WorldManager.WorldRef;
import dev.alastorkaneki.inventoryeditor.editor.PlayerInventoryStore;
import dev.alastorkaneki.inventoryeditor.editor.PlayerInventoryStore.Enchant;
import dev.alastorkaneki.inventoryeditor.editor.PlayerInventoryStore.Item;
import dev.alastorkaneki.inventoryeditor.editor.PlayerInventoryStore.PlayerData;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST = 701;
    private static final int PURPLE = 0xFFBB86FC;
    private static final int MUTED = 0xFFAAAAAA;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<WorldRef> worlds = new ArrayList<>();
    private final List<String> slotRows = new ArrayList<>();

    private TextView status;
    private ArrayAdapter<WorldRef> worldAdapter;
    private ArrayAdapter<String> slotAdapter;
    private WorldRef activeWorld;
    private PlayerData player;
    private String activeList = "Inventory";
    private int activeSlots = 36;
    private boolean playerBackupMade;

    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_REQUEST) runOnUiThread(this::refreshShizukuStatus);
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        showHome();
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override public void onBackPressed() {
        if (activeWorld != null) showHome(); else super.onBackPressed();
    }

    private void showHome() {
        activeWorld = null;
        player = null;

        LinearLayout root = root();
        root.addView(title("Bedrock Inventory Editor", 28));
        root.addView(text("Minecraft + MBLoader worlds · Shizuku protected-storage access", 14, MUTED), margin(0, 3, 0, 16));

        status = text("Checking Shizuku…", 14, Color.WHITE);
        root.addView(status);

        LinearLayout actions = row();
        Button grant = button("Grant Shizuku");
        grant.setOnClickListener(v -> requestShizuku());
        actions.addView(grant, weight());
        Button scan = button("Scan worlds");
        scan.setOnClickListener(v -> scanWorlds());
        LinearLayout.LayoutParams p = weight(); p.leftMargin = dp(8);
        actions.addView(scan, p);
        root.addView(actions, margin(0, 8, 0, 12));

        root.addView(text("Minecraft\n" + WorldSource.MINECRAFT.worldRoot + "\n\nMBLoader\n" + WorldSource.MBLOADER.worldRoot, 11, MUTED), margin(0, 0, 0, 10));
        root.addView(text("Tap a world to open its mirror (or import it first). Long-press for Re-import / Sync.", 12, 0xFFD8C5FF), margin(0, 0, 0, 8));

        ListView list = new ListView(this);
        worldAdapter = new ArrayAdapter<WorldRef>(this, android.R.layout.simple_list_item_1, worlds) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t = v.findViewById(android.R.id.text1);
                WorldRef w = getItem(position);
                boolean mirror = w != null && WorldManager.validMirror(MainActivity.this, w);
                t.setText((w == null ? "" : w.toString()) + (mirror ? "  ·  mirror ready" : ""));
                t.setTextColor(Color.WHITE); t.setTextSize(15); t.setPadding(dp(8), dp(14), dp(8), dp(14));
                return v;
            }
        };
        list.setAdapter(worldAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> openWorld(worlds.get(position), false));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showWorldMenu(worlds.get(position));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        refreshShizukuStatus();
        applyImmersive();
    }

    private void showWorldMenu(WorldRef world) {
        String mirror = WorldManager.validMirror(this, world) ? "Open existing mirror" : "Import & open";
        String[] items = new String[]{mirror, "Re-import from " + world.source.label, "Sync mirror to " + world.source.label};
        new AlertDialog.Builder(this).setTitle(world.displayName).setItems(items, (d, which) -> {
            if (which == 0) openWorld(world, false);
            else if (which == 1) openWorld(world, true);
            else syncWorld(world);
        }).show();
    }

    private void refreshShizukuStatus() {
        if (status == null) return;
        if (!ShizukuShell.binderAlive()) status.setText("Shizuku is not running");
        else if (!ShizukuShell.permissionGranted()) status.setText("Shizuku running · permission needed");
        else status.setText("Shizuku connected ✓");
    }

    private void requestShizuku() {
        if (!ShizukuShell.binderAlive()) {
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (i != null) startActivity(i);
            } catch (Throwable ignored) {}
            toast("Start Shizuku first");
            return;
        }
        if (ShizukuShell.permissionGranted()) { toast("Already connected"); scanWorlds(); return; }
        Shizuku.requestPermission(SHIZUKU_REQUEST);
    }

    private boolean preflight() {
        if (!ShizukuShell.binderAlive()) { toast("Shizuku is not running"); return false; }
        if (!ShizukuShell.permissionGranted()) { Shizuku.requestPermission(SHIZUKU_REQUEST); return false; }
        return true;
    }

    private void scanWorlds() {
        if (!preflight()) return;
        status.setText("Scanning Minecraft + MBLoader…");
        worker.execute(() -> {
            List<WorldRef> found = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (WorldSource source : WorldSource.values()) {
                try { found.addAll(WorldManager.list(source)); }
                catch (Throwable t) { errors.add(source.label + ": " + shortError(t)); }
            }
            runOnUiThread(() -> {
                worlds.clear(); worlds.addAll(found); worldAdapter.notifyDataSetChanged();
                status.setText(found.size() + " world(s)" + (errors.isEmpty() ? " found" : " · " + String.join(" · ", errors)));
            });
        });
    }

    private void openWorld(WorldRef world, boolean forceImport) {
        if (!preflight()) return;
        status.setText("Opening " + world.displayName + "…");
        worker.execute(() -> {
            try {
                File mirror = (!forceImport && WorldManager.validMirror(this, world)) ? WorldManager.mirrorDir(this, world) : WorldManager.importWorld(this, world);
                PlayerData data = PlayerInventoryStore.load(mirror);
                runOnUiThread(() -> showEditor(world, data));
            } catch (Throwable t) { runOnUiThread(() -> showError("Open failed", t)); }
        });
    }

    private void showEditor(WorldRef world, PlayerData data) {
        activeWorld = world;
        player = data;
        playerBackupMade = false;
        activeList = "Inventory";
        activeSlots = 36;

        LinearLayout root = root();
        LinearLayout top = row();
        Button back = button("‹ Worlds"); back.setOnClickListener(v -> showHome()); top.addView(back, weight());
        Button sync = button("Sync to " + world.source.label); sync.setOnClickListener(v -> syncWorld(world));
        LinearLayout.LayoutParams sp = weight(); sp.leftMargin = dp(8); top.addView(sync, sp);
        root.addView(top, margin(0, 0, 0, 12));

        root.addView(title(world.displayName, 25));
        root.addView(text(world.source.label + " · " + world.folder + " · offline mirror", 12, MUTED), margin(0, 2, 0, 10));
        root.addView(text("Changes save to the mirror immediately. Sync is explicit and creates a backup of the destination db first.", 12, 0xFFD8C5FF), margin(0, 0, 0, 10));

        Spinner sections = new Spinner(this);
        String[] labels = {"Inventory (36)", "Armor (4)", "Offhand (1)", "Ender Chest (27)"};
        ArrayAdapter<String> sectionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        sections.setAdapter(sectionAdapter);
        sections.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) { activeList = "Inventory"; activeSlots = 36; }
                else if (position == 1) { activeList = "Armor"; activeSlots = 4; }
                else if (position == 2) { activeList = "Offhand"; activeSlots = 1; }
                else { activeList = "EnderChestInventory"; activeSlots = 27; }
                renderSlots();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        root.addView(sections, margin(0, 0, 0, 8));

        ListView slots = new ListView(this);
        slots.setTag("slots");
        slotAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, slotRows) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t = v.findViewById(android.R.id.text1);
                t.setTextColor(Color.WHITE); t.setTextSize(14); t.setTypeface(Typeface.MONOSPACE); t.setPadding(dp(8), dp(12), dp(8), dp(12));
                return v;
            }
        };
        slots.setAdapter(slotAdapter);
        slots.setOnItemClickListener((parent, view, position, id) -> editSlot(position));
        root.addView(slots, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        renderSlots();
        applyImmersive();
    }

    private void renderSlots() {
        if (player == null || slotAdapter == null) return;
        List<Item> items = PlayerInventoryStore.readItems(player, activeList, activeSlots);
        slotRows.clear();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.empty()) slotRows.add(String.format("#%02d  — empty —", i));
            else slotRows.add(String.format("#%02d  %s  ×%d%s", i, item.name, item.count, item.enchants.isEmpty() ? "" : "  ench=" + item.enchants.size()));
        }
        slotAdapter.notifyDataSetChanged();
    }

    private void editSlot(int index) {
        Item item = PlayerInventoryStore.readItems(player, activeList, activeSlots).get(index);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20), 0, dp(20), 0);
        EditText id = field("Item ID", item.empty() ? "" : item.name);
        EditText count = field("Count 0-255", Integer.toString(item.empty() ? 0 : item.count));
        EditText damage = field("Damage", Integer.toString(item.empty() ? 0 : item.damage));
        EditText ench = field("Enchants: id:level, id:level", enchantText(item.enchants));
        form.addView(id); form.addView(count); form.addView(damage); form.addView(ench);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(activeList + " slot " + index)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> saveSlot(index, "", 0, 0, new ArrayList<>(), dialog));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String itemId = id.getText().toString().trim();
                    if (!itemId.isEmpty() && !itemId.contains(":")) itemId = "minecraft:" + itemId;
                    int c = Integer.parseInt(count.getText().toString().trim());
                    int d = Integer.parseInt(damage.getText().toString().trim());
                    if (c < 0 || c > 255) throw new IllegalArgumentException("Count must be 0-255");
                    if (d < Short.MIN_VALUE || d > Short.MAX_VALUE) throw new IllegalArgumentException("Damage must fit signed short");
                    saveSlot(index, itemId, c, d, parseEnchants(ench.getText().toString()), dialog);
                } catch (Throwable t) { toast(shortError(t)); }
            });
        });
        dialog.show();
    }

    private void saveSlot(int index, String id, int count, int damage, List<Enchant> enchants, AlertDialog dialog) {
        worker.execute(() -> {
            try {
                if (!playerBackupMade) {
                    PlayerInventoryStore.backupRaw(player, new File(getFilesDir(), "player-nbt-backups"));
                    playerBackupMade = true;
                }
                PlayerInventoryStore.updateItem(player, activeList, activeSlots, index, id, count, damage, enchants);
                PlayerInventoryStore.save(player);
                runOnUiThread(() -> { dialog.dismiss(); renderSlots(); toast("Mirror saved"); });
            } catch (Throwable t) { runOnUiThread(() -> showError("Save failed", t)); }
        });
    }

    private void syncWorld(WorldRef world) {
        if (!preflight()) return;
        if (!WorldManager.validMirror(this, world)) { toast("Import the world first"); return; }
        new AlertDialog.Builder(this)
                .setTitle("Sync to " + world.source.label + "?")
                .setMessage("Close " + world.source.label + " completely first. A backup of its current db will be created before replacement.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sync", (d, w) -> worker.execute(() -> {
                    try {
                        File backup = WorldManager.syncWorld(this, world);
                        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("Sync complete").setMessage("Backup:\n" + backup.getAbsolutePath()).setPositiveButton("OK", null).show());
                    } catch (Throwable t) { runOnUiThread(() -> showError("Sync failed", t)); }
                })).show();
    }

    private List<Enchant> parseEnchants(String raw) {
        ArrayList<Enchant> out = new ArrayList<>();
        if (raw.trim().isEmpty()) return out;
        for (String token : raw.split("[,\\n]+")) {
            token = token.trim(); if (token.isEmpty()) continue;
            String[] p = token.split(":", 2); if (p.length != 2) throw new IllegalArgumentException("Bad enchant: " + token);
            int id = Integer.parseInt(p[0].trim()); int lvl = Integer.parseInt(p[1].trim());
            if (id < Short.MIN_VALUE || id > Short.MAX_VALUE || lvl < Short.MIN_VALUE || lvl > Short.MAX_VALUE) throw new IllegalArgumentException("Enchant id/level max is 32767");
            out.add(new Enchant(id, lvl));
        }
        return out;
    }

    private String enchantText(List<Enchant> list) {
        StringBuilder b = new StringBuilder();
        for (Enchant e : list) { if (b.length() > 0) b.append(", "); b.append(e.id).append(':').append(e.lvl); }
        return b.toString();
    }

    private LinearLayout root() {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.setPadding(dp(16), dp(18), dp(16), dp(18)); r.setBackgroundColor(Color.BLACK); return r;
    }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); return r; }
    private TextView title(String s, int sp) { TextView t = text(s, sp, Color.WHITE); t.setTypeface(t.getTypeface(), Typeface.BOLD); return t; }
    private TextView text(String s, int sp, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b; }
    private EditText field(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setHintTextColor(0xFF777777); e.setTextColor(Color.WHITE); e.setText(value); e.setSingleLine(true); e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE)); return e; }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(48), 1); }
    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(l),dp(t),dp(r),dp(b)); return p; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void showError(String title, Throwable t) { new AlertDialog.Builder(this).setTitle(title).setMessage(shortError(t)).setPositiveButton("OK", null).show(); }
    private String shortError(Throwable t) { Throwable x=t; while(x.getCause()!=null&&x.getCause()!=x)x=x.getCause(); return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage(); }

    private void applyImmersive() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) { c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) {}
    }
}
