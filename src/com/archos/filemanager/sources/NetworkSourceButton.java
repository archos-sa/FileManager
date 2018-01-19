package com.archos.filemanager.sources;

import com.archos.environment.ArchosUtils;
import com.archos.filemanager.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;

public class NetworkSourceButton extends SourceButton {

    public NetworkSourceButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSourceInfo = new SourceInfo("smb://","");
        mIcon.setImageResource(R.drawable.ic_network_normal);
        mLine1.setText(R.string.network_storage);

        mLine2.setVisibility(View.GONE); // no available storage label for network
        mLine3.setVisibility(View.GONE); // no additional label for network
    }

    public NetworkSourceButton(Context context) {
        this(context, null);

    }


    @Override
    public void updateValidity() {
        setEnabled( ArchosUtils.isNetworkConnected(getContext()));
    }

    @Override
    public void updateAvailableStorage() {
        // nothing here for network
    }
}
