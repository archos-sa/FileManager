package com.archos.filemanager;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.archos.environment.ArchosUtils;

/**
 * Created by alexandre on 16/09/15.
 */
public class PermissionChecker {
    private static final int PERMISSION_REQUEST = 1;
    public static final String STORAGE_PERMISSION_GRANTED = "storage_permission_granted";
    private static PermissionChecker sPermissionChecker;
    Activity mActivity;

    public boolean isDialogDisplayed = false;




    /**
     * will create checker only when permission isn't granted ?
     * @param activity
     */

    @TargetApi(Build.VERSION_CODES.M)
    public void checkAndRequestPermission(Activity activity) {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            return;

        mActivity= activity;
        if(!isDialogDisplayed&& !hasStoragePermission(activity)) {
            mActivity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST);
            isDialogDisplayed = true;
        }
    }

    public static boolean hasStoragePermission(Context ct){
        return Build.VERSION.SDK_INT<Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(ct, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)== PackageManager.PERMISSION_GRANTED;
    }

    @TargetApi(Build.VERSION_CODES.M)
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M)
            return;
        if(isDialogDisplayed&& !hasStoragePermission(mActivity)){

            new AlertDialog.Builder(mActivity).setTitle(R.string.error).setMessage(R.string.error_permission_storage).setPositiveButton(R.string.allow, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    isDialogDisplayed = false;
                    // finish();

                    if(!ActivityCompat.shouldShowRequestPermissionRationale(mActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        //launch settings when "never ask again" has been checked
                        Intent in = new Intent();
                        in.setAction("android.intent.action.MANAGE_APP_PERMISSIONS");
                        in.putExtra("android.intent.extra.PACKAGE_NAME", mActivity.getPackageName());
                        try {
                            mActivity.startActivity(in);
                        } catch (SecurityException e) {
                            // Create intent to start new activity
                            in.setData(Uri.parse("package:" + mActivity.getPackageName()));
                            in.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            // start new activity to display extended information
                            mActivity.startActivity(in);

                        }
                    }
                    else checkAndRequestPermission(mActivity);
                }
            })
             .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                 @Override
                 public void onClick(DialogInterface dialogInterface, int i) {
                     android.os.Process.killProcess(android.os.Process.myPid());
                 }
             }).setCancelable(false).show();


        }
        else {
            // inform import service about the event
            Intent intent = new Intent(STORAGE_PERMISSION_GRANTED);
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            mActivity.sendBroadcast(intent);
            isDialogDisplayed = false;
        }
    }


}
