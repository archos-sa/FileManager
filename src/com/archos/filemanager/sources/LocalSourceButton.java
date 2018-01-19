package com.archos.filemanager.sources;

import com.archos.filemanager.R;

import android.content.Context;
import android.os.Environment;
import android.util.AttributeSet;
import android.view.View;

public class LocalSourceButton extends SourceButton {

    public LocalSourceButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSourceInfo(new SourceInfo("file://"
                + Environment.getExternalStorageDirectory().getPath(), context.getString(R.string.internal_storage)));
        if (mStorageSwapped==1) {
            mIcon.setImageResource(R.drawable.ic_sdcard_normal);
            mLine1.setText(R.string.sd_card_storage);
            mLine3.setText(R.string.storage_swapped_primary);
            mLine3.setVisibility(View.VISIBLE);
        }
        else {
            mIcon.setImageResource(R.drawable.ic_internal_normal);
            if (mUnifiedStorageActivated) {
                mLine1.setText(R.string.internal_storage_unified);
            } else {
                mLine1.setText(R.string.internal_storage);
            }
            mLine3.setVisibility(View.GONE);
        }
    }
    public LocalSourceButton(Context context) {
        this(context, null);
    }
    @Override
    public void updateValidity() {
        // Nothing to do, always valid, always enabled
    }

    @Override
    public void updateAvailableStorage() {
        if (isEnabled()) {
            mLine2.setText(getFormattedAvailableSize(Environment.getExternalStorageDirectory().getPath()));
        }
    }
}
