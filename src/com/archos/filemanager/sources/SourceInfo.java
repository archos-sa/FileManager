package com.archos.filemanager.sources;

import android.net.Uri;

import java.io.Serializable;

/**
 * Created by alexandre on 27/07/15.
 */
public class SourceInfo implements Serializable{
    public String mRootPath;
    public String mNiceName;
    public SourceInfo(String rootPath, String niceName){
        mRootPath = rootPath;
        mNiceName = niceName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SourceInfo that = (SourceInfo) o;

        if (mRootPath != null ? !mRootPath.equals(that.mRootPath) : that.mRootPath != null)
            return false;
        return !(mNiceName != null ? !mNiceName.equals(that.mNiceName) : that.mNiceName != null);

    }

    @Override
    public int hashCode() {
        int result = mRootPath != null ? mRootPath.hashCode() : 0;
        result = 31 * result + (mNiceName != null ? mNiceName.hashCode() : 0);
        return result;
    }
}
