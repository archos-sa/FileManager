package com.archos.filemanager.listing;

import java.util.HashMap;
import java.util.List;

import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;

import com.archos.filecorelibrary.MetaFile2;

/**
 * This class stores a map of list of MetaFiles and scrolling position.
 * The keys are Uri in String form. It does not care if there is a '/' or not at the end of the Uri
 * @author vapillon
 */
public class ListingCache {

    private static final String TAG = "ListingCache";
    private static final boolean DBG = false;

    public class SavedData {
        public final List<? extends MetaFile2> mFiles;
        public final Parcelable mListLayoutState;
        public boolean mDirty;

        SavedData(List<? extends MetaFile2> files, Parcelable listLayoutState, boolean dirty) {
            mFiles = files;
            mListLayoutState = listLayoutState;
            mDirty = dirty;
        }
    }

    final private HashMap<String, SavedData> mCacheMap = new HashMap<String, SavedData>();  

    public ListingCache() {
    }

    /**
     * 
     * @param uri: Uri... It does not care if there is a '/' or not at the end of the Uri.
     * @param files: files...
     * @param position: the scrolling position in pixels
     */
    public void put(Uri uri, List<? extends MetaFile2> files, Parcelable listLayoutState) {
        mCacheMap.put(getStringUri(uri), new SavedData(files, listLayoutState, false));
        if (DBG) debugDump("put done");
    }

    public void remove(Uri uri) {
        mCacheMap.remove(getStringUri(uri));
        if (DBG) debugDump("remove done");
    }

    public void setDirty(Uri uri) {
        SavedData data = mCacheMap.get(getStringUri(uri));
        if (data != null) {
            data.mDirty = true;
            mCacheMap.put(getStringUri(uri), data);
        }
    }

    public SavedData get(Uri uri) {
        return mCacheMap.get(getStringUri(uri));
    }

    private void debugDump(String msg) {
        Log.d(TAG, "************* "+msg+" *****************"); 
        for (String uri : mCacheMap.keySet()) {
            SavedData data = mCacheMap.get(uri);
            Log.d(TAG, "* "+data.mFiles.size()+"\t"+uri);
        }
    }

    private String getStringUri(Uri uri) {
        // We can't rely on the component using this class to always take care about if there is a "/" at the end
        // of the path or not, so we better always remove it
        String stringUri = uri.toString();
        if (stringUri.endsWith("/")) {
            stringUri = stringUri.substring(0, stringUri.length()-1);
        }
        return stringUri;
    }
}
