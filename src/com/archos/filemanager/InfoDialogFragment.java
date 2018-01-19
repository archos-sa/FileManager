package com.archos.filemanager;

import java.util.ArrayList;

import com.archos.filecorelibrary.MetaFile2;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

public class InfoDialogFragment extends DialogFragment {

    /**
     * The list of files to display.
     * Must be an ArrayList<MetaFile2>
     */
    public final static String TAG_FILE_LIST = "file_list";

    private InfoDialog mId;

    @SuppressWarnings("unchecked")
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        
        Bundle args = getArguments();
        ArrayList<MetaFile2> files = null;

        if (args != null) {
            files = (ArrayList<MetaFile2>)args.getSerializable(TAG_FILE_LIST);
        }

        if (files == null) {
            throw new IllegalArgumentException("list of files must be given to InfoDialogFragment in the aguments!");
        }
        if (files.size() < 1) {
            throw new IllegalArgumentException("list of files can not be empty!");
        }

        mId = new InfoDialog(getActivity());
        mId.setFiles(files);

        return mId;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mId != null) {
            mId.start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mId != null) {
            mId.stop();
        }
    }

}
