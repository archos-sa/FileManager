package com.archos.filemanager;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filemanager.listing.ListingFragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class NewFileOrDirectoryDialog extends DialogFragment {

    public static final String URI = "uri";

    private static final int CREATE_SUCCESS = 0;
    private static final int CREATE_FAILED = 1;

    private AlertDialog mDialog;
    private String mNewNameString;
    private Spinner mTypeChooser;

    private EditText mNameBox;
    private View mProgressBar;
    private Uri mURI;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case CREATE_SUCCESS:
                    Intent intent = new Intent(ListingFragment.ACTION_REFRESH_LISTING_FRAGMENT);
                    intent.putExtra(ListingFragment.URI_TO_REFRESH, mURI);
                    intent.putExtra(ListingFragment.PARENT_NEED_TO_BE_REFRESHED, true);
                    getActivity().sendBroadcast(intent);
                    dismiss();
                    break;
                case CREATE_FAILED:
                    if (mTypeChooser.getSelectedItemPosition() == 0) {
                        Toast.makeText(getActivity(), R.string.cannot_create_directory, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getActivity(), R.string.cannot_create_file, Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                    break;
            }
        }
    };

    private Thread mNewFileOrFolderThread = new Thread() {
        public void run() {
            FileEditor fe = FileEditorFactory.getFileEditorForUrl(Uri.withAppendedPath(mURI, mNewNameString), getActivity());
            boolean success;
            if (mTypeChooser.getSelectedItemPosition() == 0) {
                success = fe.mkdir();
            } else {
                success = fe.touchFile();
            }
            mHandler.sendEmptyMessage(success ? CREATE_SUCCESS : CREATE_FAILED);
        }
    };

    public NewFileOrDirectoryDialog () {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arg = getArguments();
        if (arg.getParcelable(URI) != null) {
            mURI = arg.getParcelable(URI);
        } else {
            throw new IllegalArgumentException("Create dialog needs a " + Uri.class.getName() + " as argument");
        }
        View newView = getActivity().getLayoutInflater().inflate(R.layout.new_file_or_folder, null);
        mNameBox = ((EditText) newView.findViewById(R.id.text));
        mProgressBar = newView.findViewById(R.id.progress_bar);
        mTypeChooser = (Spinner) newView.findViewById(R.id.type_spinner);
        mTypeChooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    mNameBox.setHint(R.string.folder_name);
                } else {
                    mNameBox.setHint(R.string.file_name);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        mDialog = new AlertDialog.Builder(getActivity())
        .setView(newView)
        .setPositiveButton(getText(android.R.string.ok), null)
        .setNegativeButton(getText(android.R.string.cancel), null)
        .create();
        mDialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mNameBox.length() > 0) {
                            // Rename the selected file/directory
                            mNewNameString = mNameBox.getText().toString();
                            mNewFileOrFolderThread.start();
                            setDisabled();
                        }
                    }
                });
            }
        });

        return mDialog;
    }

    private void setDisabled() {
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        mDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        mProgressBar.setVisibility(View.VISIBLE);
        mNameBox.setEnabled(false);
    }

}
