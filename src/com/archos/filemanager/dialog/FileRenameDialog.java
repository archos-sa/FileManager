package com.archos.filemanager.dialog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filemanager.listing.ListingFragment;

/**
 * Created by vapillon on 13/04/16.
 */
public class FileRenameDialog extends RenameDialog {

    private MetaFile2 mMetaFile;
    private Uri mUriToRefresh;

    public static final String ARG_METAFILE = "metafile";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arg = getArguments();
        if(arg.getSerializable(ARG_METAFILE)!=null){
            mMetaFile = (MetaFile2)arg.getSerializable(ARG_METAFILE);
        }
        else {
            throw new IllegalArgumentException("Rename dialog needs a " + MetaFile2.class.getName() + " as argument");
        }
        if(arg.getParcelable(ListingFragment.URI_TO_REFRESH)!=null){
            mUriToRefresh = arg.getParcelable(ListingFragment.URI_TO_REFRESH );
        }
    }

    @Override
    public String getOriginalName() {
        if (mMetaFile==null) {
            throw new IllegalStateException("The original MetaFile2 has not been set yet!");
        }
        return mMetaFile.getName();
    }

    @Override
    public void performRenaming(final String newName) {
        new Thread() {
            public void run() {
                FileEditor fe = mMetaFile.getFileEditorInstance(getActivity());
                final boolean success = fe.rename(newName);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            if (mUriToRefresh != null) {
                                Intent intent = new Intent(ListingFragment.ACTION_REFRESH_LISTING_FRAGMENT);
                                intent.putExtra(ListingFragment.URI_TO_REFRESH, mUriToRefresh);
                                getActivity().sendBroadcast(intent);
                            }
                            renameSuccess();
                        } else {
                            renameFail();
                        }
                    }
                });
            }
        }.start();
    }
}
