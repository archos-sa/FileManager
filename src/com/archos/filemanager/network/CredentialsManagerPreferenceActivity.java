package com.archos.filemanager.network;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.archos.filemanager.R;

public class CredentialsManagerPreferenceActivity extends FragmentActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.credentials_manager_activity);
        getSupportFragmentManager().beginTransaction().add(R.id.root,new CredentialsManagerPreferencesFragment()).commit();
    }
}
