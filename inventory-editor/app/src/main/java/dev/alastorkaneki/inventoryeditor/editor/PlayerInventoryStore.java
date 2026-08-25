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
import java.util.List;

public final class PlayerInventoryStore {
    public static final byte[] LOCAL_PLAYER_KEY = "~local_player".getBytes(StandardCharsets.UTF_8);
    private PlayerInventoryStore() {}

    public static final class PlayerData {
        public final File world;
        public final BedrockNbt.Root root;
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
        Options options=new Options().createIfMissing(false); DB db=factory.open(dbDir,options); try {byte[] raw=db.get(LOCAL_PLAYER_KEY); if(raw==null||raw.length==0)throw new Exception("~local_player was not found in this world"); BedrockNbt.Root root=BedrockNbt.read(raw); if(root.compound()==null)throw new Exception("~local_player is not a compound NBT record"); return new PlayerData(world,root,raw);} finally {try{db.close();}catch(Throwable ignored){}}
    }

    public static void save(PlayerData data) throws Exception {
        byte[] raw=BedrockNbt.write(data.root); Options options=new Options().createIfMissing(false); DB db=factory.open(new File(data.world,"db"),options); try{db.put(LOCAL_PLAYER_KEY,raw);}finally{try{db.close();}catch(Throwable ignored){}}
    }

    public static File backupRaw(PlayerData data, File backupRoot) throws Exception {
        File dir=new File(backupRoot, data.world.getName()); if(!dir.mkdirs()&&!dir.isDirectory())throw new Exception("Could not create player backup directory"); File out=new File(dir,"local_player_"+System.currentTimeMillis()+".nbt"); try(FileOutputStream fos=new FileOutputStream(out)){fos.write(data.original);} return out;
    }

    public static ListTag ensureList(PlayerData d,String name){
        Compound root=d.compound(); Tag t=root.get(name); if(t!=null&&t.type==BedrockNbt.LIST)return t.list(); ListTag l=new ListTag(BedrockNbt.COMPOUND);root.put(name,new Tag(BedrockNbt.LIST,l));return l;
    }

    public static List<Item> readItems(PlayerData d,String listName,int expectedSlots){
        ArrayList<Item> out=new ArrayList<>(); for(int i=0;i<expectedSlots;i++){Item empty=new Item();empty.slot=i;out.add(empty);} ListTag list=d.compound().list(listName); if(list==null||list.elementType!=BedrockNbt.COMPOUND)return out;
        for(int i=0;i<list.values.size();i++){Tag t=list.values.get(i);Compound c=t.compound();if(c==null)continue;int slot=c.number("Slot",i); if(expectedSlots==1)slot=0; if(slot<0||slot>=expectedSlots)continue;Item it=decode(c,slot);out.set(slot,it);} return out;
    }

    private static Item decode(Compound c,int slot){Item i=new Item();i.raw=c;i.slot=slot;i.name=c.string("Name","");i.count=c.number("Count",0)&0xff;i.damage=(short)c.number("Damage",0);Compound tag=c.compound("tag");if(tag!=null){ListTag ench=tag.list("ench");if(ench!=null&&ench.elementType==BedrockNbt.COMPOUND)for(Tag e:ench.values){Compound ec=e.compound();if(ec!=null)i.enchants.add(new Enchant((short)ec.number("id",0),(short)ec.number("lvl",0)));}}return i;}

    public static void updateItem(PlayerData d,String listName,int expectedSlots,int slot,String name,int count,int damage,List<Enchant> enchants){
        ListTag list=ensureList(d,listName); while(list.elementType!=BedrockNbt.COMPOUND){list.values.clear();list.elementType=BedrockNbt.COMPOUND;}
        Compound c=null;int index=-1; for(int n=0;n<list.values.size();n++){Compound x=list.values.get(n).compound();if(x==null)continue;int s=expectedSlots==1?0:x.number("Slot",n);if(s==slot){c=x;index=n;break;}}
        if(name==null||name.trim().isEmpty()||count<=0){if(index>=0)list.values.remove(index);return;}
        if(c==null){c=new Compound();c.putByte("Slot",slot);list.values.add(new Tag(BedrockNbt.COMPOUND,c));}
        c.putByte("Slot",slot);c.putString("Name",name.trim());c.putByte("Count",Math.max(0,Math.min(255,count)));c.putShort("Damage",damage);
        if(enchants!=null&&!enchants.isEmpty()){
            Compound tag=c.compound("tag");if(tag==null){tag=new Compound();c.put("tag",new Tag(BedrockNbt.COMPOUND,tag));}
            ListTag ench=new ListTag(BedrockNbt.COMPOUND);for(Enchant e:enchants){Compound ec=new Compound();ec.putShort("id",e.id);ec.putShort("lvl",e.lvl);ench.add(new Tag(BedrockNbt.COMPOUND,ec));}tag.put("ench",new Tag(BedrockNbt.LIST,ench));
        } else {Compound tag=c.compound("tag");if(tag!=null)tag.remove("ench");}
    }
}
