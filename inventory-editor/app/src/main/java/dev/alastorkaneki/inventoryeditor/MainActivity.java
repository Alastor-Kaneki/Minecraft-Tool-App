package dev.alastorkaneki.inventoryeditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.alastorkaneki.inventoryeditor.WorldManager.WorldRef;
import dev.alastorkaneki.inventoryeditor.editor.EditorHistory;
import dev.alastorkaneki.inventoryeditor.editor.ItemCatalog;
import dev.alastorkaneki.inventoryeditor.editor.PlayerInventoryStore;
import dev.alastorkaneki.inventoryeditor.editor.PlayerInventoryStore.Enchant;
import dev.alastorkaneki.inventoryeditor.editor.PlayerInventoryStore.Item;
import dev.alastorkaneki.inventoryeditor.editor.PlayerInventoryStore.PlayerData;
import dev.alastorkaneki.inventoryeditor.nbt.BedrockNbt;
import dev.alastorkaneki.inventoryeditor.nbt.BedrockNbt.Tag;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int SHIZUKU_REQUEST = 701;
    private static final int PURPLE = 0xFFBB86FC;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int SOFT = 0xFFD8C5FF;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<WorldRef> worlds = new ArrayList<>();
    private final List<String> slotRows = new ArrayList<>();
    private final List<Integer> visibleSlots = new ArrayList<>();
    private final EditorHistory history = new EditorHistory(48);

    private TextView status;
    private TextView sectionInfo;
    private EditText searchField;
    private Button undoButton;
    private Button redoButton;
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
        history.clear();

        LinearLayout root = root();
        root.addView(title("Bedrock Inventory Editor", 28));
        root.addView(text("Player Workshop 0.2.0-alpha · Minecraft + MBLoader · Shizuku", 14, MUTED), margin(0, 3, 0, 16));

        status = text("Checking Shizuku…", 14, Color.WHITE);
        root.addView(status);

        LinearLayout actions = row();
        Button grant = button("Grant Shizuku");
        grant.setOnClickListener(v -> requestShizuku());
        actions.addView(grant, weight());
        Button scan = button("Scan worlds");
        scan.setOnClickListener(v -> scanWorlds());
        LinearLayout.LayoutParams p = weight();
        p.leftMargin = dp(8);
        actions.addView(scan, p);
        root.addView(actions, margin(0, 8, 0, 12));

        root.addView(text(
                "Minecraft\n" + WorldSource.MINECRAFT.worldRoot +
                        "\n\nMBLoader\n" + WorldSource.MBLOADER.worldRoot,
                11, MUTED), margin(0, 0, 0, 10));
        root.addView(text(
                "Tap a world to open its offline mirror. Long-press for re-import or Apply.",
                12, SOFT), margin(0, 0, 0, 8));

        ListView list = new ListView(this);
        worldAdapter = new ArrayAdapter<WorldRef>(this, android.R.layout.simple_list_item_1, worlds) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView t = v.findViewById(android.R.id.text1);
                WorldRef w = getItem(position);
                boolean mirror = w != null && WorldManager.validMirror(MainActivity.this, w);
                t.setText((w == null ? "" : w.toString()) + (mirror ? "  ·  mirror ready" : ""));
                t.setTextColor(Color.WHITE);
                t.setTextSize(15);
                t.setPadding(dp(8), dp(14), dp(8), dp(14));
                return v;
            }
        };
        list.setAdapter(worldAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> openWorld(worlds.get(position), false));
        list.setOnItemLongClickListener((parent, view, position, id) -> { showWorldMenu(worlds.get(position)); return true; });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        refreshShizukuStatus();
        applyImmersive();
    }

    private void showWorldMenu(WorldRef world) {
        String mirror = WorldManager.validMirror(this, world) ? "Open existing mirror" : "Import & open";
        String[] items = new String[]{mirror,"Re-import from " + world.source.label,"Apply edited mirror to " + world.source.label};
        new AlertDialog.Builder(this).setTitle(world.displayName).setItems(items, (d, which) -> {
            if (which == 0) openWorld(world, false);
            else if (which == 1) openWorld(world, true);
            else applyWorld(world);
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
            try { Intent i = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api"); if (i != null) startActivity(i); } catch (Throwable ignored) {}
            toast("Start Shizuku first"); return;
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
            List<WorldRef> found = new ArrayList<>(); List<String> errors = new ArrayList<>();
            for (WorldSource source : WorldSource.values()) {
                try { found.addAll(WorldManager.list(source)); } catch (Throwable t) { errors.add(source.label + ": " + shortError(t)); }
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
        activeWorld = world; player = data; playerBackupMade = false; history.clear(); activeList = "Inventory"; activeSlots = 36;

        LinearLayout root = root();
        LinearLayout top = row();
        Button back = button("‹ Worlds"); back.setOnClickListener(v -> showHome()); top.addView(back, weight());
        Button apply = button("Apply to " + world.source.label); apply.setOnClickListener(v -> applyWorld(world)); LinearLayout.LayoutParams ap = weight(); ap.leftMargin = dp(8); top.addView(apply, ap);
        root.addView(top, margin(0, 0, 0, 10));

        root.addView(title(world.displayName, 24));
        root.addView(text(world.source.label + " · " + world.folder + " · offline mirror", 12, MUTED), margin(0, 2, 0, 4));
        root.addView(text("Autosave mirror · unknown item NBT preserved · signed-short illegal enchants supported", 11, SOFT), margin(0, 0, 0, 8));

        Spinner sections = new Spinner(this);
        String[] labels = {"Inventory (36)", "Armor (4)", "Offhand (1)", "Ender Chest (27)"};
        sections.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
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
        root.addView(sections, margin(0, 0, 0, 4));

        sectionInfo = text("", 12, MUTED); root.addView(sectionInfo, margin(0, 0, 0, 5));
        searchField = field("Search item / enchant / slot", "");
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){renderSlots();}
            @Override public void afterTextChanged(Editable s){}
        });
        root.addView(searchField, margin(0, 0, 0, 6));

        LinearLayout editActions=row();
        undoButton=button("Undo"); undoButton.setOnClickListener(v->undo()); editActions.addView(undoButton,weight());
        redoButton=button("Redo"); redoButton.setOnClickListener(v->redo()); LinearLayout.LayoutParams rp=weight();rp.leftMargin=dp(6);editActions.addView(redoButton,rp);
        Button tools=button("Tools"); tools.setOnClickListener(v->showTools()); LinearLayout.LayoutParams tp=weight();tp.leftMargin=dp(6);editActions.addView(tools,tp);
        root.addView(editActions,margin(0,0,0,5));

        LinearLayout inspectActions=row();
        Button stats=button("Player stats");stats.setOnClickListener(v->showPlayerStats());inspectActions.addView(stats,weight());
        Button raw=button("Raw NBT");raw.setOnClickListener(v->showRawNbt());LinearLayout.LayoutParams rawp=weight();rawp.leftMargin=dp(6);inspectActions.addView(raw,rawp);
        Button snap=button("Snapshot");snap.setOnClickListener(v->snapshotNow());LinearLayout.LayoutParams sp=weight();sp.leftMargin=dp(6);inspectActions.addView(snap,sp);
        root.addView(inspectActions,margin(0,0,0,6));

        ListView slots = new ListView(this);
        slotAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, slotRows) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent); TextView t = v.findViewById(android.R.id.text1);
                t.setTextColor(Color.WHITE); t.setTextSize(13); t.setTypeface(Typeface.MONOSPACE); t.setPadding(dp(8), dp(10), dp(8), dp(10)); return v;
            }
        };
        slots.setAdapter(slotAdapter);
        slots.setOnItemClickListener((parent, view, position, id) -> { if(position<visibleSlots.size())editSlot(visibleSlots.get(position)); });
        slots.setOnItemLongClickListener((parent,view,position,id)->{if(position<visibleSlots.size())showSlotActions(visibleSlots.get(position));return true;});
        root.addView(slots, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root); renderSlots(); applyImmersive();
    }

    private void renderSlots() {
        if (player == null || slotAdapter == null) return;
        List<Item> items = PlayerInventoryStore.readItems(player, activeList, activeSlots);
        String q=searchField==null?"":searchField.getText().toString().trim().toLowerCase(Locale.ROOT);
        slotRows.clear(); visibleSlots.clear(); int occupied=0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i); if(!item.empty())occupied++;
            String row;
            if (item.empty()) row=String.format(Locale.US,"#%02d  — empty —", i);
            else row=String.format(Locale.US,"#%02d  %s  ×%d%s",i,item.name,item.count,item.enchants.isEmpty()?"":"  "+enchantSummary(item.enchants));
            String hay=row.toLowerCase(Locale.ROOT);
            if(!q.isEmpty()&&!hay.contains(q))continue;
            visibleSlots.add(i);slotRows.add(row);
        }
        slotAdapter.notifyDataSetChanged();
        if(sectionInfo!=null)sectionInfo.setText(occupied+" / "+activeSlots+" occupied"+(q.isEmpty()?"":" · "+visibleSlots.size()+" match(es)"));
        if(undoButton!=null)undoButton.setEnabled(history.canUndo()); if(redoButton!=null)redoButton.setEnabled(history.canRedo());
    }

    private String enchantSummary(List<Enchant> list){
        StringBuilder b=new StringBuilder("ench[");int max=Math.min(3,list.size());
        for(int i=0;i<max;i++){if(i>0)b.append(',');Enchant e=list.get(i);b.append(ItemCatalog.enchantName(e.id)).append(':').append(e.lvl);}if(list.size()>max)b.append("…+").append(list.size()-max);return b.append(']').toString();
    }

    private void editSlot(int index) {
        Item item = PlayerInventoryStore.readItems(player, activeList, activeSlots).get(index);
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(20), 0, dp(20), 0);

        EditText id = field("Item ID", item.empty() ? "" : item.name);
        Button catalog=button("Browse item catalog");catalog.setOnClickListener(v->showCatalog(id));
        EditText count = field("Count 0-255", Integer.toString(item.empty() ? 0 : item.count));
        LinearLayout quick=row();
        for(int n:new int[]{1,64,127,255}){Button qb=button(Integer.toString(n));qb.setOnClickListener(v->count.setText(((Button)v).getText()));quick.addView(qb,weight());}
        EditText damage = field("Damage (signed short)", Integer.toString(item.empty() ? 0 : item.damage));
        EditText ench = field("Enchants: id/name:level, …", enchantText(item.enchants));
        TextView hint=text("Examples: sharpness:255, 17:32767, mending:-1",10,MUTED);
        form.addView(id);form.addView(catalog);form.addView(count);form.addView(quick);form.addView(damage);form.addView(ench);form.addView(hint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(activeList + " slot " + index)
                .setView(form).setNegativeButton("Cancel", null).setNeutralButton("More", null).setPositiveButton("Save", null).create();

        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showSlotActions(index));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    String itemId = id.getText().toString().trim(); if (!itemId.isEmpty() && !itemId.contains(":")) itemId = "minecraft:" + itemId;
                    int c = Integer.parseInt(count.getText().toString().trim()); int d = Integer.parseInt(damage.getText().toString().trim());
                    if (c < 0 || c > 255) throw new IllegalArgumentException("Count must be 0-255");
                    if (d < Short.MIN_VALUE || d > Short.MAX_VALUE) throw new IllegalArgumentException("Damage must fit signed short");
                    final String saveId=itemId; final int saveCount=c,saveDamage=d; final List<Enchant> saveEnchants=parseEnchants(ench.getText().toString());
                    mutate("Slot saved",()->PlayerInventoryStore.updateItem(player,activeList,activeSlots,index,saveId,saveCount,saveDamage,saveEnchants),dialog::dismiss);
                } catch (Throwable t) { toast(shortError(t)); }
            });
        });
        dialog.show();
    }

    private void showCatalog(EditText target){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),0,dp(16),0);
        EditText search=field("Search catalog",target.getText().toString());ListView list=new ListView(this);ArrayList<String> rows=new ArrayList<>();ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,rows);list.setAdapter(a);box.addView(search);box.addView(list,new LinearLayout.LayoutParams(-1,dp(420)));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Item Studio").setView(box).setNegativeButton("Close",null).create();
        Runnable refresh=()->{rows.clear();rows.addAll(ItemCatalog.search(search.getText().toString(),250));a.notifyDataSetChanged();};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int b,int c){refresh.run();}public void afterTextChanged(Editable e){}});
        list.setOnItemClickListener((p,v,pos,id)->{target.setText(rows.get(pos));d.dismiss();});refresh.run();d.show();
    }

    private void showSlotActions(int slot){
        Item item=PlayerInventoryStore.readItems(player,activeList,activeSlots).get(slot);
        ArrayList<String> labels=new ArrayList<>();
        labels.add("Edit");
        if(!item.empty()){
            labels.add("Duplicate to first empty");labels.add("Copy to slot…");labels.add("Move to slot…");labels.add("Swap with slot…");labels.add("Set count 64");labels.add("Set count 127");labels.add("Set count 255");labels.add("Clear slot");
        }
        new AlertDialog.Builder(this).setTitle(activeList+" slot "+slot).setItems(labels.toArray(new String[0]),(d,which)->{
            String action=labels.get(which);
            if(action.equals("Edit")){editSlot(slot);return;}
            if(action.equals("Duplicate to first empty")){mutate("Duplicated",()->{int dst=PlayerInventoryStore.duplicateToFirstEmpty(player,activeList,activeSlots,slot);runOnUiThread(()->toast("Duplicated to slot "+dst));},null);return;}
            if(action.equals("Copy to slot…")){promptTarget("Copy to slot",slot,t->mutate("Copied",()->PlayerInventoryStore.copySlot(player,activeList,activeSlots,slot,t),null));return;}
            if(action.equals("Move to slot…")){promptTarget("Move to slot",slot,t->mutate("Moved",()->PlayerInventoryStore.moveSlot(player,activeList,activeSlots,slot,t),null));return;}
            if(action.equals("Swap with slot…")){promptTarget("Swap with slot",slot,t->mutate("Swapped",()->PlayerInventoryStore.swapSlots(player,activeList,activeSlots,slot,t),null));return;}
            if(action.startsWith("Set count ")){int n=Integer.parseInt(action.substring("Set count ".length()));mutate("Count set to "+n,()->PlayerInventoryStore.updateItem(player,activeList,activeSlots,slot,item.name,n,item.damage,item.enchants),null);return;}
            if(action.equals("Clear slot"))confirm("Clear slot "+slot+"?","This can be undone in this editor session.",()->mutate("Slot cleared",()->PlayerInventoryStore.updateItem(player,activeList,activeSlots,slot,"",0,0,new ArrayList<>()),null));
        }).show();
    }

    private interface TargetAction{void run(int target);}
    private void promptTarget(String title,int current,TargetAction action){
        EditText e=field("Target slot 0-"+(activeSlots-1),Integer.toString(current));
        new AlertDialog.Builder(this).setTitle(title).setView(e).setNegativeButton("Cancel",null).setPositiveButton("Go",(d,w)->{
            try{int t=Integer.parseInt(e.getText().toString().trim());if(t<0||t>=activeSlots)throw new IllegalArgumentException("Slot must be 0-"+(activeSlots-1));action.run(t);}catch(Throwable x){toast(shortError(x));}
        }).show();
    }

    private void showTools(){
        String[] tools={"Sort current section A–Z","Set every occupied stack to 64","Set every occupied stack to 127","Set every occupied stack to 255","Clear current section","Save player NBT snapshot","Player stats","Raw player NBT","Enchant ID legend"};
        new AlertDialog.Builder(this).setTitle("Workshop tools · "+activeList).setItems(tools,(d,w)->{
            if(w==0)mutate("Section sorted",()->PlayerInventoryStore.sortSection(player,activeList,activeSlots),null);
            else if(w==1)mutate("Stacks set to 64",()->PlayerInventoryStore.setAllCounts(player,activeList,activeSlots,64),null);
            else if(w==2)mutate("Stacks set to 127",()->PlayerInventoryStore.setAllCounts(player,activeList,activeSlots,127),null);
            else if(w==3)mutate("Stacks set to 255",()->PlayerInventoryStore.setAllCounts(player,activeList,activeSlots,255),null);
            else if(w==4)confirm("Clear "+activeList+"?","Every normal slot in this section will be emptied. Undo remains available for this session.",()->mutate("Section cleared",()->PlayerInventoryStore.clearSection(player,activeList,activeSlots),null));
            else if(w==5)snapshotNow(); else if(w==6)showPlayerStats(); else if(w==7)showRawNbt(); else showText("Enchant IDs / names",ItemCatalog.enchantLegend(),false);
        }).show();
    }

    private void snapshotNow(){
        if(player==null)return;worker.execute(()->{try{File f=PlayerInventoryStore.backupCurrent(player,new File(getFilesDir(),"player-nbt-backups"));runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Snapshot saved").setMessage(f.getAbsolutePath()).setPositiveButton("OK",null).show());}catch(Throwable t){runOnUiThread(()->showError("Snapshot failed",t));}});
    }

    private void showPlayerStats(){
        if(player==null)return;
        String[] keys={"PlayerLevel","PlayerLevelProgress","Health","MaxHealth","SelectedInventorySlot","GameMode","DimensionId","SpawnX","SpawnY","SpawnZ"};
        LinkedHashMap<String,EditText> fields=new LinkedHashMap<>();LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(18),0,dp(18),0);
        for(String key:keys){Tag t=player.compound().get(key);if(t!=null&&t.value instanceof Number){EditText e=field(key+" ("+BedrockNbt.typeName(t.type)+")",String.valueOf(t.value));fields.put(key,e);form.addView(e);}}
        if(fields.isEmpty()){toast("No known numeric player stat tags found");return;}
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("Player stats").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            Map<String,String> values=new LinkedHashMap<>();for(Map.Entry<String,EditText> e:fields.entrySet())values.put(e.getKey(),e.getValue().getText().toString().trim());
            mutate("Player stats saved",()->{for(Map.Entry<String,String> e:values.entrySet())PlayerInventoryStore.updateNumericExisting(player,e.getKey(),e.getValue());},null);
        }).show();
    }

    private void showRawNbt(){if(player==null)return;showText("~local_player raw NBT",BedrockNbt.pretty(player.root),true);}
    private void showText(String title,String body,boolean monospace){
        TextView t=text(body,11,Color.WHITE);if(monospace)t.setTypeface(Typeface.MONOSPACE);t.setTextIsSelectable(true);t.setPadding(dp(12),dp(8),dp(12),dp(8));ScrollView s=new ScrollView(this);s.addView(t);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(s).setNegativeButton("Close",null).setPositiveButton("Copy",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText(title,body));toast("Copied");}));d.show();
    }

    private interface Mutation{void run() throws Exception;}
    private void mutate(String label,Mutation mutation,Runnable afterSuccess){
        if(player==null)return;
        worker.execute(()->{
            byte[] before=null;
            try{
                before=PlayerInventoryStore.snapshot(player);
                if(!playerBackupMade){PlayerInventoryStore.backupRaw(player,new File(getFilesDir(),"player-nbt-backups"));playerBackupMade=true;}
                mutation.run();PlayerInventoryStore.save(player);history.record(before);
                runOnUiThread(()->{renderSlots();if(afterSuccess!=null)afterSuccess.run();toast(label);});
            }catch(Throwable t){if(before!=null)try{PlayerInventoryStore.restore(player,before);}catch(Throwable ignored){}runOnUiThread(()->showError("Edit failed",t));}
        });
    }

    private void undo(){
        if(player==null||!history.canUndo())return;worker.execute(()->{try{byte[] current=PlayerInventoryStore.snapshot(player);byte[] target=history.undo(current);if(target==null)return;PlayerInventoryStore.restore(player,target);PlayerInventoryStore.save(player);runOnUiThread(()->{renderSlots();toast("Undo");});}catch(Throwable t){runOnUiThread(()->showError("Undo failed",t));}});
    }
    private void redo(){
        if(player==null||!history.canRedo())return;worker.execute(()->{try{byte[] current=PlayerInventoryStore.snapshot(player);byte[] target=history.redo(current);if(target==null)return;PlayerInventoryStore.restore(player,target);PlayerInventoryStore.save(player);runOnUiThread(()->{renderSlots();toast("Redo");});}catch(Throwable t){runOnUiThread(()->showError("Redo failed",t));}});
    }

    private void confirm(String title,String message,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->yes.run()).show();}

    private void applyWorld(WorldRef world) {
        if (!preflight()) return;
        if (!WorldManager.validMirror(this, world)) { toast("Import the world first"); return; }
        new AlertDialog.Builder(this)
                .setTitle("Apply to " + world.source.label + "?")
                .setMessage("The editor will try true same-folder replacement first. If Android blocks target-package writes, it exports the edited mirror as a .mcworld and hands it to " + world.source.label + " for import instead.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> worker.execute(() -> {
                    try {
                        if (WorldManager.canTargetRunAs(world.source)) {
                            File backup = WorldManager.syncWorldInPlace(this, world);
                            runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("In-place sync complete").setMessage("The existing " + world.source.label + " world was updated.\n\nBackup:\n" + backup.getAbsolutePath()).setPositiveButton("OK", null).show());
                        } else {
                            File mcworld = WorldExportImporter.exportMirror(this, world);
                            runOnUiThread(() -> new AlertDialog.Builder(this)
                                    .setTitle("Import edited copy")
                                    .setMessage(world.source.label + " does not permit run-as, so Android 15 blocks a separate shell/app from replacing its protected world folder. An edited .mcworld is ready; the target app will perform its own protected write.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Open .mcworld", (x, y) -> { try { WorldExportImporter.openInTarget(this, world, mcworld); } catch (Throwable t) { showError("Import handoff failed", t); } }).show());
                        }
                    } catch (Throwable t) { runOnUiThread(() -> showError("Apply failed", t)); }
                })).show();
    }

    private List<Enchant> parseEnchants(String raw) {
        ArrayList<Enchant> out = new ArrayList<>(); if (raw.trim().isEmpty()) return out;
        for (String token : raw.split("[,\\n]+")) {
            token = token.trim(); if (token.isEmpty()) continue; int colon=token.lastIndexOf(':');
            if (colon <= 0 || colon == token.length()-1) throw new IllegalArgumentException("Bad enchant: " + token);
            int id=ItemCatalog.enchantId(token.substring(0,colon)); int lvl=Integer.parseInt(token.substring(colon+1).trim());
            if (id < Short.MIN_VALUE || id > Short.MAX_VALUE || lvl < Short.MIN_VALUE || lvl > Short.MAX_VALUE) throw new IllegalArgumentException("Enchant id/level must fit signed short");
            out.add(new Enchant(id, lvl));
        }
        return out;
    }

    private String enchantText(List<Enchant> list) {StringBuilder b = new StringBuilder();for (Enchant e : list) {if (b.length() > 0) b.append(", ");b.append(e.id).append(':').append(e.lvl);}return b.toString();}

    private LinearLayout root() {LinearLayout r = new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(16), dp(18), dp(16), dp(18));r.setBackgroundColor(Color.BLACK);return r;}
    private LinearLayout row() {LinearLayout r = new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private TextView title(String s, int sp) {TextView t = text(s, sp, Color.WHITE);t.setTypeface(t.getTypeface(), Typeface.BOLD);return t;}
    private TextView text(String s, int sp, int color) {TextView t = new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}
    private Button button(String s) {Button b = new Button(this);b.setText(s);b.setAllCaps(false);return b;}
    private EditText field(String hint, String value) {EditText e = new EditText(this);e.setHint(hint);e.setHintTextColor(0xFF777777);e.setTextColor(Color.WHITE);e.setText(value);e.setSingleLine(true);e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));return e;}
    private LinearLayout.LayoutParams weight() {return new LinearLayout.LayoutParams(0, dp(46), 1);}
    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);p.setMargins(dp(l), dp(t), dp(r), dp(b));return p;}
    private int dp(int v) {return Math.round(v * getResources().getDisplayMetrics().density);}
    private void toast(String s) {Toast.makeText(this, s, Toast.LENGTH_SHORT).show();}
    private void showError(String title, Throwable t) {new AlertDialog.Builder(this).setTitle(title).setMessage(shortError(t)).setPositiveButton("OK", null).show();}
    private String shortError(Throwable t) {Throwable x = t;while (x.getCause() != null && x.getCause() != x) x = x.getCause();return x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage();}

    private void applyImmersive() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) {}
    }
}
