package dev.alastorkaneki.inventoryeditor.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lossless little-endian NBT reader/writer plus safe inspection/copy helpers for Bedrock records. */
public final class BedrockNbt {
    public static final byte END=0, BYTE=1, SHORT=2, INT=3, LONG=4, FLOAT=5, DOUBLE=6, BYTE_ARRAY=7, STRING=8, LIST=9, COMPOUND=10, INT_ARRAY=11, LONG_ARRAY=12;
    private BedrockNbt() {}

    public static final class Tag {
        public final byte type; public Object value;
        public Tag(byte type,Object value){this.type=type;this.value=value;}
        public int asInt(int def){ if(value instanceof Number)return ((Number)value).intValue(); return def; }
        public long asLong(long def){ if(value instanceof Number)return ((Number)value).longValue(); return def; }
        public String asString(String def){ return value instanceof String ? (String)value : def; }
        public Compound compound(){ return type==COMPOUND ? (Compound)value : null; }
        public ListTag list(){ return type==LIST ? (ListTag)value : null; }
    }
    public static final class Compound extends LinkedHashMap<String,Tag> {
        public Tag tag(String n){return get(n);} public Compound compound(String n){Tag t=get(n);return t==null?null:t.compound();} public ListTag list(String n){Tag t=get(n);return t==null?null:t.list();}
        public String string(String n,String d){Tag t=get(n);return t==null?d:t.asString(d);} public int number(String n,int d){Tag t=get(n);return t==null?d:t.asInt(d);}
        public void putByte(String n,int v){put(n,new Tag(BYTE,(byte)v));} public void putShort(String n,int v){put(n,new Tag(SHORT,(short)v));} public void putInt(String n,int v){put(n,new Tag(INT,v));}
        public void putLong(String n,long v){put(n,new Tag(LONG,v));} public void putFloat(String n,float v){put(n,new Tag(FLOAT,v));} public void putDouble(String n,double v){put(n,new Tag(DOUBLE,v));}
        public void putString(String n,String v){put(n,new Tag(STRING,v==null?"":v));}
    }
    public static final class ListTag {
        public byte elementType; public final ArrayList<Tag> values=new ArrayList<>();
        public ListTag(byte e){elementType=e;} public int size(){return values.size();} public Tag get(int i){return values.get(i);} public void add(Tag t){if(values.isEmpty())elementType=t.type; if(t.type!=elementType)throw new IllegalArgumentException("NBT list type mismatch"); values.add(t);} }
    public static final class Root { public String name; public Tag tag; Root(String n,Tag t){name=n;tag=t;} public Compound compound(){return tag.compound();} }

    public static Root read(byte[] data) throws IOException {
        In in=new In(data); int type=in.u8(); if(type==END)throw new IOException("NBT root cannot be TAG_End"); String name=in.str(); Tag root=new Tag((byte)type, readPayload(in,(byte)type,0)); return new Root(name,root);
    }
    public static byte[] write(Root root) throws IOException {
        Out out=new Out(); out.u8(root.tag.type); out.str(root.name==null?"":root.name); writePayload(out,root.tag,0); return out.bytes();
    }

    public static Tag deepCopy(Tag tag) {
        if(tag==null)return null;
        Object v=tag.value;
        switch(tag.type){
            case BYTE_ARRAY: v=((byte[])v).clone(); break;
            case INT_ARRAY: v=((int[])v).clone(); break;
            case LONG_ARRAY: v=((long[])v).clone(); break;
            case LIST:{
                ListTag src=(ListTag)v,dst=new ListTag(src.elementType);
                for(Tag t:src.values)dst.values.add(deepCopy(t));
                v=dst; break;
            }
            case COMPOUND:{
                Compound src=(Compound)v,dst=new Compound();
                for(Map.Entry<String,Tag> e:src.entrySet())dst.put(e.getKey(),deepCopy(e.getValue()));
                v=dst; break;
            }
            default: break;
        }
        return new Tag(tag.type,v);
    }

    public static Compound deepCopyCompound(Compound c){ return c==null?null:deepCopy(new Tag(COMPOUND,c)).compound(); }

    public static String pretty(Root root) {
        StringBuilder b=new StringBuilder();
        b.append(typeName(root.tag.type)).append('(').append(root.name==null?"":root.name).append(")\n");
        prettyTag(b,root.tag,0);
        return b.toString();
    }

    private static void prettyTag(StringBuilder b,Tag t,int depth){
        if(depth>24){indent(b,depth).append("…\n");return;}
        if(t==null){b.append("null\n");return;}
        switch(t.type){
            case COMPOUND:{
                Compound c=t.compound(); b.append("{\n");
                for(Map.Entry<String,Tag> e:c.entrySet()){
                    indent(b,depth+1).append(e.getKey()).append(": ").append(typeName(e.getValue().type)).append(' ');
                    prettyTag(b,e.getValue(),depth+1);
                }
                indent(b,depth).append("}\n"); break;
            }
            case LIST:{
                ListTag l=t.list(); b.append('[').append(l.size()).append(" × ").append(typeName(l.elementType)).append("]\n");
                int max=Math.min(l.size(),128);
                for(int i=0;i<max;i++){indent(b,depth+1).append('[').append(i).append("] ");prettyTag(b,l.get(i),depth+1);}
                if(l.size()>max)indent(b,depth+1).append("… ").append(l.size()-max).append(" more\n");
                break;
            }
            case BYTE_ARRAY:b.append("<byte[").append(((byte[])t.value).length).append("]>\n");break;
            case INT_ARRAY:b.append("<int[").append(((int[])t.value).length).append("]>\n");break;
            case LONG_ARRAY:b.append("<long[").append(((long[])t.value).length).append("]>\n");break;
            case STRING:b.append('"').append(escape(String.valueOf(t.value))).append("\"\n");break;
            default:b.append(String.valueOf(t.value)).append('\n');
        }
    }

    private static StringBuilder indent(StringBuilder b,int n){for(int i=0;i<n;i++)b.append("  ");return b;}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t").replace("\"","\\\"");}
    public static String typeName(byte t){switch(t){case END:return"END";case BYTE:return"BYTE";case SHORT:return"SHORT";case INT:return"INT";case LONG:return"LONG";case FLOAT:return"FLOAT";case DOUBLE:return"DOUBLE";case BYTE_ARRAY:return"BYTE_ARRAY";case STRING:return"STRING";case LIST:return"LIST";case COMPOUND:return"COMPOUND";case INT_ARRAY:return"INT_ARRAY";case LONG_ARRAY:return"LONG_ARRAY";default:return"TYPE_"+t;}}

    private static Object readPayload(In in,byte type,int depth) throws IOException {
        if(depth>128)throw new IOException("NBT nesting too deep");
        switch(type){
            case BYTE:return (byte)in.u8(); case SHORT:return (short)in.i16(); case INT:return in.i32(); case LONG:return in.i64();
            case FLOAT:return Float.intBitsToFloat(in.i32()); case DOUBLE:return Double.longBitsToDouble(in.i64());
            case BYTE_ARRAY:{int n=in.length();return in.bytes(n);} case STRING:return in.str();
            case LIST:{byte et=(byte)in.u8();int n=in.length();ListTag l=new ListTag(et);for(int i=0;i<n;i++)l.values.add(new Tag(et,readPayload(in,et,depth+1)));return l;}
            case COMPOUND:{Compound c=new Compound();while(true){byte t=(byte)in.u8();if(t==END)break;String name=in.str();c.put(name,new Tag(t,readPayload(in,t,depth+1)));}return c;}
            case INT_ARRAY:{int n=in.length();int[] a=new int[n];for(int i=0;i<n;i++)a[i]=in.i32();return a;}
            case LONG_ARRAY:{int n=in.length();long[] a=new long[n];for(int i=0;i<n;i++)a[i]=in.i64();return a;}
            default:throw new IOException("Unsupported NBT tag type "+type);
        }
    }
    private static void writePayload(Out out,Tag tag,int depth) throws IOException {
        if(depth>128)throw new IOException("NBT nesting too deep");
        switch(tag.type){
            case BYTE:out.u8(((Number)tag.value).byteValue());break; case SHORT:out.i16(((Number)tag.value).shortValue());break; case INT:out.i32(((Number)tag.value).intValue());break; case LONG:out.i64(((Number)tag.value).longValue());break;
            case FLOAT:out.i32(Float.floatToIntBits(((Number)tag.value).floatValue()));break; case DOUBLE:out.i64(Double.doubleToLongBits(((Number)tag.value).doubleValue()));break;
            case BYTE_ARRAY:{byte[] a=(byte[])tag.value;out.i32(a.length);out.raw(a);break;} case STRING:out.str((String)tag.value);break;
            case LIST:{ListTag l=(ListTag)tag.value;out.u8(l.elementType);out.i32(l.values.size());for(Tag t:l.values){if(t.type!=l.elementType)throw new IOException("NBT list type mismatch");writePayload(out,t,depth+1);}break;}
            case COMPOUND:{Compound c=(Compound)tag.value;for(Map.Entry<String,Tag> e:c.entrySet()){Tag t=e.getValue();if(t==null||t.type==END)continue;out.u8(t.type);out.str(e.getKey());writePayload(out,t,depth+1);}out.u8(END);break;}
            case INT_ARRAY:{int[] a=(int[])tag.value;out.i32(a.length);for(int x:a)out.i32(x);break;} case LONG_ARRAY:{long[] a=(long[])tag.value;out.i32(a.length);for(long x:a)out.i64(x);break;}
            default:throw new IOException("Unsupported NBT tag type "+tag.type);
        }
    }

    private static final class In {
        final ByteArrayInputStream in; In(byte[] b){in=new ByteArrayInputStream(b);} int u8()throws IOException{int x=in.read();if(x<0)throw new EOFException();return x;} byte[] bytes(int n)throws IOException{if(n<0||n>64*1024*1024)throw new IOException("Invalid NBT length "+n);byte[] b=new byte[n];int o=0,r;while(o<n&&(r=in.read(b,o,n-o))>0)o+=r;if(o!=n)throw new EOFException();return b;} int i16()throws IOException{return u8()|(u8()<<8);} int i32()throws IOException{return u8()|(u8()<<8)|(u8()<<16)|(u8()<<24);} long i64()throws IOException{return ((long)u8())|((long)u8()<<8)|((long)u8()<<16)|((long)u8()<<24)|((long)u8()<<32)|((long)u8()<<40)|((long)u8()<<48)|((long)u8()<<56);} int length()throws IOException{int n=i32();if(n<0||n>8_000_000)throw new IOException("Invalid NBT collection length "+n);return n;} String str()throws IOException{int n=i16()&0xffff;return new String(bytes(n),StandardCharsets.UTF_8);} }
    private static final class Out {
        final ByteArrayOutputStream out=new ByteArrayOutputStream(); void u8(int x){out.write(x&255);} void i16(int x){u8(x);u8(x>>>8);} void i32(int x){u8(x);u8(x>>>8);u8(x>>>16);u8(x>>>24);} void i64(long x){for(int i=0;i<8;i++)u8((int)(x>>>(8*i)));} void raw(byte[] b){out.write(b,0,b.length);} void str(String s)throws IOException{byte[] b=s.getBytes(StandardCharsets.UTF_8);if(b.length>65535)throw new IOException("NBT string too long");i16(b.length);raw(b);} byte[] bytes(){return out.toByteArray();} }
}
