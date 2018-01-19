package com.archos.filemanager.dialog;

import android.content.Intent;
import android.os.Bundle;

import com.archos.filemanager.ShortcutDb;
import com.archos.filemanager.network.shortcuts.ShortcutsFragment;

/**
 * Created by vapillon on 13/04/16.
 */
public class ShortcutRenameDialog extends RenameDialog {

    public static final String ARG_SHORTCUT_ID = "shortcut_id";

    long mShortcutId;
    String mOriginalName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arg = getArguments();
        mShortcutId = arg.getLong(ARG_SHORTCUT_ID, -1);
        if (mShortcutId < 0) {
            throw new IllegalArgumentException("Rename dialog needs a ARG_SHORTCUT_ID as argument");
        }
        mOriginalName = ShortcutDb.STATIC.getShortcutName(mShortcutId);
    }

    @Override
    public String getOriginalName() {
        return mOriginalName;
    }

    @Override
    public void performRenaming(String newName) {
        final boolean success = ShortcutDb.STATIC.renameShortcut(mShortcutId, newName);
        mHandler.sendEmptyMessage(success ? RENAME_SUCCESS : RENAME_FAILED);
        mHandler.post(new Runnable() {
            public void run() {
                if (success) {
                    getActivity().sendBroadcast(new Intent(ShortcutsFragment.ACTION_REFRESH_SHORTCUTS_FRAGMENT));
                    getActivity().sendBroadcast(new Intent(ShortcutsFragment.ACTION_STOP_ACTION_MODE));
                    renameSuccess();
                } else {
                    renameFail();
                }
            }
        });
    }
}
