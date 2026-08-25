package dev.alastorkaneki.inventoryeditor.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/** Small in-memory serialized-NBT history. The mirror is saved only after a successful mutation. */
public final class EditorHistory {
    private final int limit;
    private final Deque<byte[]> undo=new ArrayDeque<>();
    private final Deque<byte[]> redo=new ArrayDeque<>();

    public EditorHistory(int limit){this.limit=Math.max(1,limit);}
    public void clear(){undo.clear();redo.clear();}
    public boolean canUndo(){return !undo.isEmpty();}
    public boolean canRedo(){return !redo.isEmpty();}
    public int undoDepth(){return undo.size();}
    public int redoDepth(){return redo.size();}

    public void record(byte[] before){
        if(before==null)return;
        undo.push(before.clone());
        while(undo.size()>limit)undo.removeLast();
        redo.clear();
    }

    public byte[] undo(byte[] current){
        if(undo.isEmpty())return null;
        if(current!=null)redo.push(current.clone());
        return undo.pop();
    }

    public byte[] redo(byte[] current){
        if(redo.isEmpty())return null;
        if(current!=null){undo.push(current.clone());while(undo.size()>limit)undo.removeLast();}
        return redo.pop();
    }
}
