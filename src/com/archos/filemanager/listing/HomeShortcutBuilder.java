package com.archos.filemanager.listing;

import android.content.Intent;


import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filemanager.FileManagerUtils;
import com.archos.filemanager.RootActivity;


/**
 * Created by alexandre on 10/03/17.
 */

public class HomeShortcutBuilder {
    public static void createShortcutForMetaFile(MetaFile2 metaFile){
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(ArchosUtils.getGlobalContext(), RootActivity.class);
        intent.putExtra(RootActivity.EXTRA_FOLDER_TO_OPEN, metaFile.getUri().toString());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(metaFile.isFile()&&!"application/zip".equals(metaFile.getMimeType())) {
            intent.putExtra(RootActivity.EXTRA_EXTENSION, metaFile.getExtension());
            intent.putExtra(RootActivity.EXTRA_MIMETYPE, metaFile.getMimeType());
        }else if("application/zip".equals(metaFile.getMimeType())){

            intent.putExtra(RootActivity.EXTRA_FOLDER_TO_OPEN, "zip://" + metaFile.getUri().getPath());

        }
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, metaFile.getNameWithoutExtension());
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(ArchosUtils.getGlobalContext(),
                        FileManagerUtils.getIconResIdForFile(metaFile)));

        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        addIntent.putExtra("duplicate", false);
        ArchosUtils.getGlobalContext().sendBroadcast(addIntent);
    }
}
