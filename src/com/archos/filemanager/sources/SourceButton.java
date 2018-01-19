package com.archos.filemanager.sources;

import java.io.File;

import com.archos.environment.ArchosUtils;
import com.archos.environment.SystemPropertiesProxy;
import com.archos.filemanager.R;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class SourceButton extends FrameLayout {

    protected final View mRoot;
    protected final ImageView mIcon;
    protected final TextView mLine1;
    protected final TextView mLine2;
    protected final TextView mLine3;

    protected boolean mUnifiedStorageActivated = SystemPropertiesProxy.getBoolean("persist.sys.archos.unioned", false);

    // Check if internal and external storage are inverted or not
    protected int mStorageSwapped = SystemPropertiesProxy.getInt("vold_swap_state",0);
    protected SourceInfo mSourceInfo;

    protected SourceButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater li = LayoutInflater.from(context);
        mRoot = li.inflate(R.layout.main_list_item, this, false);
        addView(mRoot);

        // CAUTION: The click is handled at "this" level, which is different than mRoot (mRoot is in "this")
        this.setBackgroundResource(R.drawable.main_list_item_background);
        this.setClickable(true);
        this.setFocusable(true);
        this.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        mIcon = (ImageView) mRoot.findViewById(R.id.icon);
        mLine1 = (TextView) mRoot.findViewById(R.id.text_line1);
        mLine2 = (TextView) mRoot.findViewById(R.id.text_line2);
        mLine3 = (TextView) mRoot.findViewById(R.id.text_line3);
    }
    public SourceButton(Context context) {
        this(context, null);
    }
    public void setSourceInfo(SourceInfo sourceInfo){
        mSourceInfo = sourceInfo;
        mIcon.setImageResource(R.drawable.ic_usb_normal);
        mLine1.setText(mSourceInfo.mNiceName);
        updateValidity();
    }

    /**
     * Update if the storage is available or not. Will likely change the setEnable() of the view
     */
    public void updateValidity() {
        if (isMounted(mSourceInfo.mRootPath)) {
            mLine2.setText(getFormattedAvailableSize(mSourceInfo.mRootPath));
            mLine2.setVisibility(View.VISIBLE);
            setEnabled(true);
        }
        else {
            mLine2.setVisibility(View.GONE);
            setEnabled(false);
        }
    }

    /**
     * Update the displayed available storage
     */

    public void updateAvailableStorage(){};




    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        this.setAlpha(enabled ? 1f : 0.4f);
    }

    protected String getFormattedAvailableSize(String storagePath) {
        long size = getAvailableSize(storagePath);
        if(size==-1)
            return "";
        return Formatter.formatFileSize(getContext(), size);
    }

    private static long getAvailableSize(String storagePath) {
        long size = -1;
        try {
            final StatFs stat = new StatFs(storagePath);
            final long blockSize = stat.getBlockSize();
            final long availableBlocks = stat.getAvailableBlocks();
            size = availableBlocks * blockSize;
        }
        catch (IllegalArgumentException e) {
            //Log.e(LOG_TAG, "getAvailableSize : can't get available size for path " + storagePath);
        }
        return size;
    }

    /**
     * Some old weird code...
     * @param mountPoint
     * @return
     */
    protected static boolean isMounted(String mountPoint) {

        return true;
    }

    public void setDrawable(int drawable) {
        mIcon.setImageResource(drawable);
    }

    public SourceInfo getSourceInfo() {
        return mSourceInfo;
    }
}
