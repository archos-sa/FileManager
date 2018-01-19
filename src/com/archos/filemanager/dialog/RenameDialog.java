package com.archos.filemanager.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.archos.filemanager.R;


public abstract class RenameDialog extends DialogFragment {

    public static final int RENAME_SUCCESS = 0;
    public static final int RENAME_FAILED = 1;

    private AlertDialog mDialog;
    private EditText mNameBox;
    private View mProgressBar;

    /** to be implemented by child classes */
    public abstract String getOriginalName();
    public abstract void performRenaming(String newName);

    protected Handler mHandler =  new Handler();

    public RenameDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View renameView = getActivity().getLayoutInflater().inflate(R.layout.enter_text, null);
        renameView.findViewById(R.id.message).setVisibility(View.GONE);

        mNameBox = (EditText) renameView.findViewById(R.id.text);
        mNameBox.setHint(R.string.file_name);
        mNameBox.setText(getOriginalName());
        mNameBox.setSelection(mNameBox.getText().length()); // nicer for user to have the selection at the end of the current name

        mProgressBar = renameView.findViewById(R.id.progress_bar);

        mDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.rename)
                .setView(renameView)
                .setPositiveButton(getText(android.R.string.ok), null)
                .setNegativeButton(getText(android.R.string.cancel), null)
                .create();

        mDialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mNameBox.length() > 0 && !mNameBox.getText().toString().equals(getOriginalName())) {
                            // Rename the selected file/directory
                            performRenaming(mNameBox.getText().toString());
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

    protected void renameSuccess() {
        dismiss();
    }

    protected void renameFail() {
        Toast.makeText(getActivity(), R.string.cannot_rename, Toast.LENGTH_SHORT).show();
        dismiss();
    }

}
