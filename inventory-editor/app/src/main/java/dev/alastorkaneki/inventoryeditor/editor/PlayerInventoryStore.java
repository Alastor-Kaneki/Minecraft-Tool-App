package dev.alastorkaneki.inventoryeditor.editor;

import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

import dev.alastorkaneki.inventoryeditor.nbt.BedrockNbt;
import dev.alastorkaneki.inventoryeditor.nbt.BedrockNbt.Compound;
import dev.alastorkaneki.inventoryeditor.nbt.BedrockNbt.ListTag;
import dev.alastorkaneki.inventoryeditor.nbt.BedrockNbt.Tag;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PlayerInventoryStore {
    public static final byte[] LOCAL_PLAYER_KEY = "~local_player".getBytes(StandardCharsets.UTF_8);
    private PlayerInventoryStore() {}

    public static final class PlayerData {
        public final File world;
        public BedrockNbt.Root root;
        public final byte[] original;
        PlayerData(File world, BedrockNbt.Root root, byte[] original){this.world=world;this.root=root;this.original=original;}
        public Compound compound(){return root.compound();}
    }

    public static final class Item {
        public String name=""; public int count; public int damage; public int slot; public final List<Enchant> enchants=new ArrayList<>(); public Compound raw;
        public boolean empty(){return name==null||name.isEmpty()||count<=0;}
    }
    public static final class Enchant { public int id,lvl; public Enchant(int id,int lvl){this.id=id;this.lvl=lvl;} }

    public static PlayerData load(File world) throws Exception {
        File dbDir=new File(world,"db"); if(!dbDir.isDirectory()) throw new Exception("Bedrock db directory is missing");
        Options options=new Options().createIfMissing(false); DB db=factory.open(dbDir,options);
        try {
            byte[] raw=db.get(LOCAL_PLAYER_KEY);
            if(raw==null||raw.length==0)throw new Exception("~local_player was not found in this world");
            BedrockNbt.Root root=BedrockNbt.read(raw);
            if(root.compound()==null)throw new Exception("~local_player is not a compound NBT record");
            return new PlayerData(world,root,raw);
        } finally {try{db.close();}catch(Throwable ignored){}}
    }

    public static void save(PlayerData data) throws Exception {
        byte[] raw=BedrockNbt.write(data.root); Options options=new Options().createIfMissing(false); DB db=factory.open(new File(data.world,"db"),options);
        try{db.put(LOCAL_PLAYER_KEY,raw);}finally{try{db.close();}catch(Throwable ignored){}}
    }

    public static byte[] snapshot(PlayerData data) throws Exception { return BedrockNbt.write(data.root); }
    public static void restore(PlayerData data, byte[] raw) throws Exception {
        BedrockNbt.Root next=BedrockNbt.read(raw);
        if(next.compound()==null)throw new Exception("Snapshot is not compound NBT");
        data.root=next;
    }

    public static File backupRaw(PlayerData data, File backupRoot) throws Exception {
        return writeBackup(data,backupRoot,data.original,"original");
    }
    public static File backupCurrent(PlayerData data, File backupRoot) throws Exception {
        return writeBackup(data,backupRoot,snapshot(data),"snapshot");
    }
    private static File writeBackup(PlayerData data,File backupRoot,byte[] raw,String kind)throws Exception{
        File dir=new File(backupRoot, data.world.getName()); if(!dir.mkdirs()&&!dir.isDirectory())throw new Exception("Could not create player backup directory");
        File out=new File(dir,kind+"_local_player_"+System.currentTimeMillis()+".nbt");
        try(FileOutputStream fos=new FileOutputStream(out)){fos.write(raw);} return out;
    }

    public static ListTag ensureList(PlayerData d,String name){
        Compound root=d.compound(); Tag t=root.get(name); if(t!=null&&t.type==BedrockNbt.LIST)return t.list();
        ListTag l=new ListTag(BedrockNbt.COMPOUND);root.put(name,new Tag(BedrockNbt.LIST,l));return l;
    }

    public static List<Item> readItems(PlayerData d,String listName,int expectedSlots){
        ArrayList<Item> out=new ArrayList<>(); for(int i=0;i<expectedSlots;i++){Item empty=new Item();empty.slot=i;out.add(empty);}
        ListTag list=d.compound().list(listName); if(list==null||list.elementType!=BedrockNbt.COMPOUND)return out;
        for(int i=0;i<list.values.size();i++){
            Tag t=list.values.get(i);Compound c=t.compound();if(c==null)continue;
            int slot=slotOf(c,i,expectedSlots); if(slot<0||slot>=expectedSlots)continue;
            out.set(slot,decode(c,slot));
        }
        return out;
    }

    private static Item decode(Compound c,int slot){
        Item i=new Item();i.raw=c;i.slot=slot;i.name=c.string("Name","");i.count=c.number("Count",0)&0xff;i.damage=(short)c.number("Damage",0);
        Compound tag=c.compound("tag");if(tag!=null){ListTag ench=tag.list("ench");if(ench!=null&&ench.elementType==BedrockNbt.COMPOUND)for(Tag e:ench.values){Compound ec=e.compound();if(ec!=null)i.enchants.add(new Enchant((short)ec.number("id",0),(short)ec.number("lvl",0)));}}
        return i;
    }

    public static void updateItem(PlayerData d,String listName,int expectedSlots,int slot,String name,int count,int damage,List<Enchant> enchants){
        checkSlot(slot,expectedSlots);
        ListTag list=ensureList(d,listName); forceCompoundList(list);
        Compound c=null;int index=-1;
        for(int n=0;n<list.values.size();n++){Compound x=list.values.get(n).compound();if(x==null)continue;int s=slotOf(x,n,expectedSlots);if(s==slot){c=x;index=n;break;}}
        if(name==null||name.trim().isEmpty()||count<=0){if(index>=0)list.values.remove(index);return;}
        if(c==null){c=new Compound();c.putByte("Slot",slot);list.values.add(new Tag(BedrockNbt.COMPOUND,c));}
        c.putByte("Slot",slot);c.putString("Name",name.trim());c.putByte("Count",Math.max(0,Math.min(255,count)));c.putShort("Damage",damage);
        if(enchants!=null&&!enchants.isEmpty()){
            Compound tag=c.compound("tag");if(tag==null){tag=new Compound();c.put("tag",new Tag(BedrockNbt.COMPOUND,tag));}
            ListTag ench=new ListTag(BedrockNbt.COMPOUND);
            for(Enchant e:enchants){Compound ec=new Compound();ec.putShort("id",e.id);ec.putShort("lvl",e.lvl);ench.add(new Tag(BedrockNbt.COMPOUND,ec));}
            tag.put("ench",new Tag(BedrockNbt.LIST,ench));
        } else {Compound tag=c.compound("tag");if(tag!=null)tag.remove("ench");}
    }

    public static int duplicateToFirstEmpty(PlayerData d,String listName,int expectedSlots,int source) {
        List<Item> items=readItems(d,listName,expectedSlots); checkSlot(source,expectedSlots);
        if(items.get(source).empty())throw new IllegalArgumentException("Source slot is empty");
        for(int i=0;i<items.size();i++)if(items.get(i).empty()){copySlot(d,listName,expectedSlots,source,i);return i;}
        throw new IllegalStateException("No empty slot in this section");
    }

    public static void copySlot(PlayerData d,String listName,int expectedSlots,int source,int target){
        checkSlot(source,expectedSlots);checkSlot(target,expectedSlots); if(source==target)return;
        Compound src=findCompound(d,listName,expectedSlots,source);
        if(src==null)throw new IllegalArgumentException("Source slot is empty");
        putCompound(d,listName,expectedSlots,target,BedrockNbt.deepCopyCompound(src));
    }

    public static void moveSlot(PlayerData d,String listName,int expectedSlots,int source,int target){
        if(source==target)return; copySlot(d,listName,expectedSlots,source,target); removeSlot(d,listName,expectedSlots,source);
    }

    public static void swapSlots(PlayerData d,String listName,int expectedSlots,int a,int b){
        checkSlot(a,expectedSlots);checkSlot(b,expectedSlots);if(a==b)return;
        Compound ca=findCompound(d,listName,expectedSlots,a), cb=findCompound(d,listName,expectedSlots,b);
        Compound aCopy=BedrockNbt.deepCopyCompound(ca),bCopy=BedrockNbt.deepCopyCompound(cb);
        removeSlot(d,listName,expectedSlots,a);removeSlot(d,listName,expectedSlots,b);
        if(aCopy!=null)putCompound(d,listName,expectedSlots,b,aCopy);
        if(bCopy!=null)putCompound(d,listName,expectedSlots,a,bCopy);
    }

    public static void clearSection(PlayerData d,String listName,int expectedSlots){
        ListTag list=d.compound().list(listName);if(list==null||list.elementType!=BedrockNbt.COMPOUND)return;
        for(int i=list.values.size()-1;i>=0;i--){Compound c=list.values.get(i).compound();if(c==null)continue;int s=slotOf(c,i,expectedSlots);if(s>=0&&s<expectedSlots)list.values.remove(i);}
    }

    public static void setAllCounts(PlayerData d,String listName,int expectedSlots,int count){
        if(count<1||count>255)throw new IllegalArgumentException("Count must be 1-255");
        ListTag list=d.compound().list(listName);if(list==null||list.elementType!=BedrockNbt.COMPOUND)return;
        for(int i=0;i<list.values.size();i++){Compound c=list.values.get(i).compound();if(c==null)continue;int s=slotOf(c,i,expectedSlots);if(s>=0&&s<expectedSlots&&!c.string("Name","").isEmpty())c.putByte("Count",count);}
    }

    public static void sortSection(PlayerData d,String listName,int expectedSlots){
        ListTag list=ensureList(d,listName); forceCompoundList(list);
        ArrayList<Compound> known=new ArrayList<>();
        for(int i=0;i<list.values.size();i++){Compound c=list.values.get(i).compound();if(c==null)continue;int s=slotOf(c,i,expectedSlots);if(s>=0&&s<expectedSlots&&!c.string("Name","").isEmpty())known.add(BedrockNbt.deepCopyCompound(c));}
        known.sort(Comparator.comparing(c->c.string("Name","").toLowerCase()));
        clearSection(d,listName,expectedSlots);
        for(int i=0;i<known.size()&&i<expectedSlots;i++)putCompound(d,listName,expectedSlots,i,known.get(i));
    }

    public static void updateNumericExisting(PlayerData d,String key,String raw){
        Tag t=d.compound().get(key);if(t==null||!(t.value instanceof Number))throw new IllegalArgumentException(key+" is not numeric");
        switch(t.type){
            case BedrockNbt.BYTE:t.value=(byte)Long.parseLong(raw);break;
            case BedrockNbt.SHORT:t.value=(short)Long.parseLong(raw);break;
            case BedrockNbt.INT:t.value=Integer.parseInt(raw);break;
            case BedrockNbt.LONG:t.value=Long.parseLong(raw);break;
            case BedrockNbt.FLOAT:t.value=Float.parseFloat(raw);break;
            case BedrockNbt.DOUBLE:t.value=Double.parseDouble(raw);break;
            default:throw new IllegalArgumentException(key+" is not a supported numeric tag");
        }
    }

    private static int slotOf(Compound c,int fallback,int expectedSlots){return expectedSlots==1?0:c.number("Slot",fallback);}
    private static void checkSlot(int slot,int expectedSlots){if(slot<0||slot>=expectedSlots)throw new IllegalArgumentException("Slot must be 0-"+(expectedSlots-1));}
    private static void forceCompoundList(ListTag l){if(l.elementType!=BedrockNbt.COMPOUND){l.values.clear();l.elementType=BedrockNbt.COMPOUND;}}

    private static Compound findCompound(PlayerData d,String listName,int expectedSlots,int slot){
        ListTag list=d.compound().list(listName);if(list==null||list.elementType!=BedrockNbt.COMPOUND)return null;
        for(int i=0;i<list.values.size();i++){Compound c=list.values.get(i).compound();if(c!=null&&slotOf(c,i,expectedSlots)==slot)return c;}return null;
    }

    private static void removeSlot(PlayerData d,String listName,int expectedSlots,int slot){
        ListTag list=d.compound().list(listName);if(list==null||list.elementType!=BedrockNbt.COMPOUND)return;
        for(int i=list.values.size()-1;i>=0;i--){Compound c=list.values.get(i).compound();if(c!=null&&slotOf(c,i,expectedSlots)==slot)list.values.remove(i);}
    }

    private static void putCompound(PlayerData d,String listName,int expectedSlots,int slot,Compound c){
        checkSlot(slot,expectedSlots);removeSlot(d,listName,expectedSlots,slot);if(c==null)return;
        c.putByte("Slot",slot);ListTag list=ensureList(d,listName);forceCompoundList(list);list.values.add(new Tag(BedrockNbt.COMPOUND,c));
    }
}
