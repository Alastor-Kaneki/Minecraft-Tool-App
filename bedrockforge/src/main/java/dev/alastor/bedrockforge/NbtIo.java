package dev.alastor.bedrockforge;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NbtIo {
    static final byte END=0, BYTE=1, SHORT=2, INT=3, LONG=4, FLOAT=5, DOUBLE=6,
            BYTE_ARRAY=7, STRING=8, LIST=9, COMPOUND=10, INT_ARRAY=11, LONG_ARRAY=12;

    static final class Tag {
        byte type;
        Object value;
        Tag(byte type, Object value) { this.type = type; this.value = value; }
        @SuppressWarnings("unchecked") Map<String, Tag> compound() { return (Map<String, Tag>) value; }
        ListTag list() { return (ListTag) value; }
    }

    static final class ListTag {
        byte elementType;
        final List<Tag> values = new ArrayList<>();
        ListTag(byte elementType) { this.elementType = elementType; }
    }

    static final class Document {
        String rootName;
        Tag root;
        Document(String rootName, Tag root) { this.rootName = rootName; this.root = root; }
    }

    static Document read(byte[] data) {
        Reader r = new Reader(data);
        byte type = r.u8();
        if (type == END) throw new IllegalArgumentException("NBT root cannot be TAG_End");
        String name = r.string16();
        Tag root = new Tag(type, r.payload(type));
        if (r.pos != data.length) {
            // Bedrock player NBT normally consumes the full record. Trailing bytes are unsafe to discard.
            throw new IllegalArgumentException("NBT has " + (data.length - r.pos) + " trailing byte(s)");
        }
        return new Document(name, root);
    }

    static byte[] write(Document doc) {
        Writer w = new Writer();
        w.u8(doc.root.type);
        w.string16(doc.rootName == null ? "" : doc.rootName);
        w.payload(doc.root);
        return w.bytes();
    }

    static Tag byteTag(int v) { return new Tag(BYTE, (byte) v); }
    static Tag shortTag(int v) { return new Tag(SHORT, (short) v); }
    static Tag intTag(int v) { return new Tag(INT, v); }
    static Tag stringTag(String v) { return new Tag(STRING, v); }
    static Tag compoundTag() { return new Tag(COMPOUND, new LinkedHashMap<String, Tag>()); }
    static Tag listTag(byte elementType) { return new Tag(LIST, new ListTag(elementType)); }

    private static final class Reader {
        final byte[] b;
        int pos;
        Reader(byte[] b) { this.b = b; }
        void need(int n) { if (n < 0 || pos + n > b.length) throw new IllegalArgumentException("Truncated NBT at byte " + pos); }
        byte u8() { need(1); return b[pos++]; }
        int u16() { need(2); int v=(b[pos]&255)|((b[pos+1]&255)<<8); pos+=2; return v; }
        int i32() { need(4); int v=(b[pos]&255)|((b[pos+1]&255)<<8)|((b[pos+2]&255)<<16)|(b[pos+3]<<24); pos+=4; return v; }
        long i64() { need(8); long v=0; for(int i=0;i<8;i++) v|=((long)b[pos+i]&255L)<<(8*i); pos+=8; return v; }
        String string16() { int n=u16(); need(n); String s=new String(b,pos,n,StandardCharsets.UTF_8); pos+=n; return s; }
        int count() { int n=i32(); if(n<0 || n>10_000_000) throw new IllegalArgumentException("Invalid NBT length " + n); return n; }
        Object payload(byte type) {
            switch (type) {
                case BYTE: return u8();
                case SHORT: return (short)u16();
                case INT: return i32();
                case LONG: return i64();
                case FLOAT: return Float.intBitsToFloat(i32());
                case DOUBLE: return Double.longBitsToDouble(i64());
                case BYTE_ARRAY: {
                    int n=count(); need(n); byte[] a=new byte[n]; System.arraycopy(b,pos,a,0,n); pos+=n; return a;
                }
                case STRING: return string16();
                case LIST: {
                    byte et=u8(); int n=count(); ListTag list=new ListTag(et);
                    for(int i=0;i<n;i++) list.values.add(new Tag(et,payload(et)));
                    return list;
                }
                case COMPOUND: {
                    Map<String,Tag> m=new LinkedHashMap<>();
                    while(true) {
                        byte t=u8(); if(t==END) break;
                        String name=string16(); m.put(name,new Tag(t,payload(t)));
                    }
                    return m;
                }
                case INT_ARRAY: {
                    int n=count(); int[] a=new int[n]; for(int i=0;i<n;i++) a[i]=i32(); return a;
                }
                case LONG_ARRAY: {
                    int n=count(); long[] a=new long[n]; for(int i=0;i<n;i++) a[i]=i64(); return a;
                }
                default: throw new IllegalArgumentException("Unsupported NBT type " + type);
            }
        }
    }

    private static final class Writer {
        final ByteArrayOutputStream out=new ByteArrayOutputStream();
        void u8(int v) { out.write(v & 255); }
        void u16(int v) { u8(v); u8(v>>>8); }
        void i32(int v) { u8(v);u8(v>>>8);u8(v>>>16);u8(v>>>24); }
        void i64(long v) { for(int i=0;i<8;i++) u8((int)(v>>>(8*i))); }
        void string16(String s) {
            byte[] a=s.getBytes(StandardCharsets.UTF_8);
            if(a.length>65535) throw new IllegalArgumentException("NBT string too long");
            u16(a.length); out.write(a,0,a.length);
        }
        void payload(Tag tag) {
            switch(tag.type) {
                case BYTE: u8(((Number)tag.value).byteValue()); break;
                case SHORT: u16(((Number)tag.value).shortValue()); break;
                case INT: i32(((Number)tag.value).intValue()); break;
                case LONG: i64(((Number)tag.value).longValue()); break;
                case FLOAT: i32(Float.floatToIntBits(((Number)tag.value).floatValue())); break;
                case DOUBLE: i64(Double.doubleToLongBits(((Number)tag.value).doubleValue())); break;
                case BYTE_ARRAY: { byte[] a=(byte[])tag.value; i32(a.length); out.write(a,0,a.length); break; }
                case STRING: string16((String)tag.value); break;
                case LIST: {
                    ListTag l=tag.list(); u8(l.elementType); i32(l.values.size());
                    for(Tag child:l.values) { if(child.type!=l.elementType) throw new IllegalArgumentException("Mixed NBT list"); payload(child); }
                    break;
                }
                case COMPOUND: {
                    for(Map.Entry<String,Tag> e:tag.compound().entrySet()) {
                        Tag child=e.getValue(); u8(child.type); string16(e.getKey()); payload(child);
                    }
                    u8(END); break;
                }
                case INT_ARRAY: { int[] a=(int[])tag.value; i32(a.length); for(int v:a)i32(v); break; }
                case LONG_ARRAY: { long[] a=(long[])tag.value; i32(a.length); for(long v:a)i64(v); break; }
                default: throw new IllegalArgumentException("Unsupported NBT type " + tag.type);
            }
        }
        byte[] bytes() { return out.toByteArray(); }
    }
}
