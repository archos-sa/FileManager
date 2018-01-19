package com.archos.filemanager.listing;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filemanager.R;
import com.archos.filecorelibrary.contentstorage.DocumentUriBuilder;
import com.archos.filecorelibrary.zip.ZipUtils;
import com.archos.filemanager.PasteAndDragBin;
import com.archos.filemanager.sources.SourceInfo;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

public class LocalListingFragment extends ListingFragment {

    private static final int READ_REQUEST_CODE = 1001;
    private Uri mRootUri = Uri.parse(
            "file://"
                    + Environment.getExternalStorageDirectory().getPath()
                    + "/"); // important to end with "/"
    private boolean mHasAlreadyAsked = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if(savedInstanceState!=null)
            mSourceInfo = (SourceInfo) savedInstanceState.getSerializable("mSourceInfo");
        super.onCreate(savedInstanceState);
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("mSourceInfo",mSourceInfo);
        super.onSaveInstanceState(outState);

    }

    @Override
    protected long getListingTimeOut() {
        return 3000;
    }

    @Override
    protected String getRootName(Context context) {
        return mSourceInfo.mNiceName;
    }
    protected SourceInfo mSourceInfo;
    public void setSourceInfo(SourceInfo sourceInfo){
        mSourceInfo= sourceInfo;
        if(mState != State.INIT) {
            if(mUriStack!=null)
                mUriStack.clear();
            mBreadCrumbTrailerView.setRoot(getRootUri(), getRootName(getActivity()));
            startListing(Uri.parse(mSourceInfo.mRootPath), 0, null);
        }

    }
    public SourceInfo getSourceInfo(){
        return mSourceInfo;
    }

    @Override
    protected Uri getStartingUri() {
        return getRootUri();
    }

    @Override
    protected Uri getRootUri() {
        return Uri.parse(mSourceInfo.mRootPath);
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }
    @Override
    protected boolean onFileClick(MetaFile2 file, View v){
        if ("application/zip".equals(file.getMimeType())) {
            //checking if update file
            try {
                ZipFile zf = new ZipFile(file.getUri().getPath());
                if (zf.getEntry("META-INF/com/google/android/updater-script") != null) {
                    Intent intent = new Intent();
                    intent.setAction(android.content.Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(file.getUri().toString()), "application/fw-update");
                    intent.putExtra("confirmation", true);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    getActivity().startActivity(intent);
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return super.onFileClick(file, v);
    }


    @Override
    public void onListingFatalError(Exception e, ListingEngine.ErrorEnum errorCode) {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.KITKAT) {
            if(mCurrentUri.equals(getRootUri())&&!"content".equals(mCurrentUri.getScheme())){
                if ((mSourceInfo.mRootPath != null && !new File(mSourceInfo.mRootPath).exists())&&(mCurrentUri==null||!"content".equals(mCurrentUri.getScheme()))) {
                    Uri uri = DocumentUriBuilder.getUriFromRootPath(getActivity(),mSourceInfo.mRootPath);
                    startListing(uri, 100, null);
                }
            }
            else if (!mHasAlreadyAsked && "content".equals(mCurrentUri.getScheme()) && (errorCode == ListingEngine.ErrorEnum.ERROR_AUTHENTICATION || errorCode == ListingEngine.ErrorEnum.ERROR_NO_PERMISSION)) {
                ExternalPermissionDialog externalPermissionDialog = new ExternalPermissionDialog();
                externalPermissionDialog.setResultListener(READ_REQUEST_CODE, this);
                externalPermissionDialog.show(getFragmentManager(),"");
                mHasAlreadyAsked = true;
            }
            else
                super.onListingFatalError(e, errorCode);
        }
        else
            super.onListingFatalError(e, errorCode);
    }

    @Override
    public void onListingFileInfoUpdate(Uri uri, MetaFile2 metaFile2) {

    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (isActive()) {
           menu.add(0,R.string.gain_write_access, 20, R.string.gain_write_access).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (isActive()) {
            menu.findItem(R.string.gain_write_access).setVisible(getCurrentMetaFile() != null
                    && !getCurrentMetaFile().canWrite() && !getCurrentMetaFile().isRemote());
        }
        super.onPrepareOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (isActive()) {
            switch (item.getItemId()) {
                case R.string.gain_write_access:
                    ExternalPermissionDialog externalPermissionDialog = new ExternalPermissionDialog();
                    externalPermissionDialog.setResultListener(READ_REQUEST_CODE, this);
                    externalPermissionDialog.show(getFragmentManager(),"");
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if(requestCode==READ_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {

                //Set directory as default in preferences
                Uri treeUri = intent.getData();
                //grant write permissions
                getActivity().getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
               mCurrentUri = treeUri;
                startListing(mCurrentUri, 100, null);
                // mCurrentDirectory = treeUri;
               // listFiles(false);
               // getLoaderManager().restartLoader(0, null, this);

            } //
            // else displayFailPage();
        }
    }
    @Override
    public boolean goBackToRoot(){
        mHasAlreadyAsked = false;
        return super.goBackToRoot();
    }
}
