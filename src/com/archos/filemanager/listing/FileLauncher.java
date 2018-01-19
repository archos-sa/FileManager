package com.archos.filemanager.listing;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.FileUriExposedException;
import android.util.Log;
import android.widget.Toast;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.Utils;
import com.archos.filemanager.R;

/**
 * Created by alexandre on 10/03/17.
 */

public class FileLauncher {
    private static final String TAG = "FileLauncher";


    public static void openFile(Uri uri,String extension, String mimeType, Context context){
        // If the file is a local file, Play/view the file
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "*/" + extension;
        }
        Log.d(TAG, "data=" + uri);
        Log.d(TAG, "type=" + mimeType);

        intent.setDataAndType(uri, mimeType);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // We can try to open file with another application, with data type
            // set to *, because Android does not handle well Mime-type in some cases
            Intent intent2 = new Intent(Intent.ACTION_VIEW);
            intent2.setDataAndType(uri, "*/*");
            try {
                context.startActivity(intent2);
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, R.string.no_application_to_open_file, Toast.LENGTH_SHORT).show();
            }
        }catch(FileUriExposedException exception){//oops, we're not system
               uri =Utils.getContentUriFromFileURI(ArchosUtils.getGlobalContext(),uri);
                if(uri!=null)
                    openFile(uri,extension, mimeType, context);
        }
    }
}
