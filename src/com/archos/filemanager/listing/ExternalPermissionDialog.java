package com.archos.filemanager.listing;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

/**
 * Created by alexandre on 28/11/17.
 */

public class ExternalPermissionDialog extends DialogFragment {

    private int mRequestCode;
    private Object mFragmentOrActivity;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(com.archos.filecorelibrary.R.string.acquire_external_usb_permission).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION );
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if(mFragmentOrActivity instanceof Activity)
                    ((Activity)mFragmentOrActivity).startActivityForResult(intent, mRequestCode);
                else
                    ((Fragment)mFragmentOrActivity).startActivityForResult(intent, mRequestCode);

            }
        }).setNegativeButton(android.R.string.cancel, null).show();

        return builder.create();

    }

    public void setResultListener(int requestCode, Object fragmentOrActivity) {
        mRequestCode = requestCode;
        mFragmentOrActivity = fragmentOrActivity;
    }
}