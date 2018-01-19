package com.archos.filemanager;

import android.app.Application;
import android.content.Context;

import com.archos.environment.ArchosUtils;

/**
 * Created by alexandre on 07/12/17.
 */

public class CustomApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        ArchosUtils.setGlobalContext(base);
    }
}
