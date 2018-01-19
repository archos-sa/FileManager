package com.archos.filemanager;

import java.util.ArrayList;
import java.util.List;

import com.archos.filecorelibrary.MetaFile2;

public class PasteAndDragBin {
    public static List<MetaFile2> pastebinMetafiles;
    public static List<MetaFile2> dragbinMetafiles;
    public static FileManagerService.FileActionEnum currentPasteMode;
    public static List<MetaFile2> getPastebinMetafiles(){
        if(pastebinMetafiles==null)
            pastebinMetafiles = new ArrayList<MetaFile2>();
        return pastebinMetafiles;
    }
    public static void addToPastebin(List<MetaFile2> toPaste, FileManagerService.FileActionEnum pasteMode){
        currentPasteMode = pasteMode;
        if(pastebinMetafiles==null)
            pastebinMetafiles = new ArrayList<MetaFile2>();
        pastebinMetafiles.clear();
        pastebinMetafiles.addAll(toPaste);    
    }

    public static void addToDragBin(List<MetaFile2> toDrag){
        if(dragbinMetafiles==null)
            dragbinMetafiles = new ArrayList<MetaFile2>();
        dragbinMetafiles.clear();
        dragbinMetafiles.addAll(toDrag);
    }
    public static void clearPastebin(){
        if(pastebinMetafiles!=null)
            pastebinMetafiles.clear();
       
    }

    public static List<MetaFile2> getDragbinMetafiles() {
        return dragbinMetafiles;
    }
}
