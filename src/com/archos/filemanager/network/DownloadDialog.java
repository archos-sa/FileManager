package com.archos.filemanager.network;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filemanager.FileManagerService;
import com.archos.filemanager.R;

import java.util.ArrayList;

public class DownloadDialog extends DialogFragment {

    private MetaFile2 mMetaFile;
    public static final String METAFILE = "metafile";

    public DownloadDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arg = getArguments();
        if(arg!=null&&arg.getSerializable(METAFILE )!=null){
            mMetaFile = (MetaFile2)arg.getSerializable(METAFILE);
        }
        else
            throw new IllegalArgumentException("Download dialog needs a "+MetaFile2.class.getName()+" as argument");
        View downloadView = getActivity().getLayoutInflater().inflate(R.layout.download_layout, null);
        final Spinner location = (Spinner) downloadView.findViewById(R.id.spinner_location);
        final String[] choices = new String[4];
        choices[0] = Environment.DIRECTORY_DOWNLOADS;
        choices[1] = Environment.DIRECTORY_MUSIC;
        choices[2] = Environment.DIRECTORY_MOVIES;
        choices[3] = Environment.DIRECTORY_PICTURES;
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getActivity(), android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        location.setAdapter(adapter);
        String mimeType = mMetaFile.getMimeType()!=null&&!mMetaFile.getMimeType().isEmpty()? mMetaFile.getMimeType():"*/*";
        if(mimeType.startsWith("video"))
            location.setSelection(2);
        if(mimeType.startsWith("audio"))
            location.setSelection(1);
        if(mimeType.startsWith("image"))
            location.setSelection(3);
        final CheckBox openAtTheEnd = ((CheckBox)downloadView.findViewById(R.id.open_file));
        openAtTheEnd.setChecked(FileManagerService.fileManagerService.getOpenAtTheEnd());
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setView(downloadView)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ArrayList<MetaFile2> filesToPaste = new ArrayList<MetaFile2>();
                        Uri endPoint;
                        int position = location.getSelectedItemPosition();
                        if (position >= 0 && position < choices.length) {
                            endPoint = Uri.fromFile(Environment.getExternalStoragePublicDirectory(choices[position]));
                        } else {
                            throw new IllegalArgumentException("Unknown endpoint");
                        }

                        filesToPaste.add(mMetaFile);
                        FileManagerService.fileManagerService.copy(filesToPaste,endPoint);
                        FileManagerService.fileManagerService.setOpenAtTheEnd(openAtTheEnd.isChecked());
                    }
                })
                .create();
        return dialog;
    }

}
