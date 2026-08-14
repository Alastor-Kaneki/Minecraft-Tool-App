package dev.packsmith.mt;

import java.util.UUID;
import bin.mt.plugin.api.editor.TextEditor;

final class EditorUtil {
    private EditorUtil() {}
    static String uuid() { return UUID.randomUUID().toString(); }
    static String selectedOrAll(TextEditor e) {
        int a=e.getSelectionStart(), b=e.getSelectionEnd();
        if (a==b) return e.subText(0,e.length());
        return e.subText(Math.min(a,b),Math.max(a,b));
    }
    static void replaceSelectionOrAll(TextEditor e,String s) {
        int a=e.getSelectionStart(), b=e.getSelectionEnd();
        if (a==b) e.replaceText(0,e.length(),s);
        else e.replaceText(Math.min(a,b),Math.max(a,b),s);
    }
    static void replaceWhole(TextEditor e,String s){ e.replaceText(0,e.length(),s); }
    static int[] version(String v) {
        String[] p=v.split("\\."); int[] out={1,0,0};
        for(int i=0;i<3 && i<p.length;i++) try{out[i]=Math.max(0,Integer.parseInt(p[i]));}catch(Exception ignored){}
        return out;
    }
    static String normalizeId(String s) {
        s=s.trim().toLowerCase().replace(' ','_');
        StringBuilder o=new StringBuilder();
        for(int i=0;i<s.length();i++){char c=s.charAt(i); if((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='_'||c=='-'||c=='.'||c==':')o.append(c);}
        return o.toString();
    }
}
