package com.archos.filemanager.network.shortcuts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.filecorelibrary.samba.Share;
import com.archos.filecorelibrary.samba.Workgroup;
import com.archos.filemanager.R;
import com.archos.filemanager.ShortcutDb;
import com.archos.filemanager.network.NetworkRootFragment;

import java.util.ArrayList;
import java.util.List;

public class ShortcutsFragment extends Fragment implements SambaDiscovery.Listener, LoaderCallbacks<Cursor> {

    private static final String TAG = "ShortcutsFragment";
    private static boolean DBG = true;

    public static final String ACTION_REFRESH_SHORTCUTS_FRAGMENT = "ACTION_REFRESH_SHORTCUTS_FRAGMENT";
    public static final String ACTION_STOP_ACTION_MODE = "ACTION_STOP_ACTION_MODE";

    private AsyncTask<Void, Void, Void> mCheckShortcutAvailabilityTask;

    public interface OnShortcutTapListener {
        public void onShortcutTap(Uri uri);
        public void onUnavailableShortcutTap(Uri uri);
    }

    private RecyclerView mShortcutsList;
    private LinearLayoutManager mLayoutManager;
    private ShortcutsAdapter mAdapter;
    private OnShortcutTapListener mOnShortcutTapListener;

    /**
     * Handle selection of Shortcuts and display the related ActionMode items
     */
    private ShortcutActionModeManager mActionModeManager;

    private BroadcastReceiver mReceiver = new BroadcastReceiver(){
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ACTION_REFRESH_SHORTCUTS_FRAGMENT)){
                updateShortcutsList();
            }
            else if (intent.getAction().equals(ACTION_STOP_ACTION_MODE)) {
                mActionModeManager.stopActionMode();
            }
        }
    };

    public ShortcutsFragment() {
        super();
        setRetainInstance(false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // NetworkRootFragment handles the link between SambaDiscoveryFragment and ShortcutsFragment.
        // This call is the only way I found to do it that works in all cases, but it is ugly because it requires ShortcutsFragment to know NetworkRootFragment...
        if (getParentFragment() instanceof NetworkRootFragment) {
            ((NetworkRootFragment)getParentFragment()).onShortcutsFragmentAttached(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Cancel the ActionMode when quitting this fragment.
        // NOTE: ShortcutActionModeManager is smart enough to not clear the file selection if onDestroy is called for a screen rotation
        mActionModeManager.stopActionMode();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.shortcuts_fragment, container, false);

        mShortcutsList = (RecyclerView)v.findViewById(R.id.shortcuts_list);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mShortcutsList.setLayoutManager(mLayoutManager);
        mShortcutsList.setHasFixedSize(false); // URL line can wrap on several lines

        mAdapter = new ShortcutsAdapter();
        Log.d(TAG, "onCreateView mOnShortcutTapListener="+mOnShortcutTapListener );
        mAdapter.setOnShortcutTapListener(mOnShortcutTapListener);
        mShortcutsList.setAdapter(mAdapter);

        mActionModeManager = new ShortcutActionModeManager(this, mAdapter);
        mAdapter.setOnSelectionChangedListener(mActionModeManager);

        if (savedInstanceState!=null) {
            mAdapter.onRestoreInstanceState(savedInstanceState);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REFRESH_SHORTCUTS_FRAGMENT);
        filter.addAction(ACTION_STOP_ACTION_MODE);
        getActivity().registerReceiver(mReceiver, filter);

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        // initializing the loader in onResume instead of in onActivityCreated,
        // see http://stackoverflow.com/questions/11293441/android-loadercallbacks-onloadfinished-called-twice/22183247
        getLoaderManager().initLoader(0, (Bundle)null, this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mAdapter.onSaveInstanceState(outState);
    }

    public void setOnShortcutTapListener(OnShortcutTapListener listener) {
        Log.d(TAG, "setOnShortcutTapListener "+listener);
        mOnShortcutTapListener = listener;
        // Update adapter if it is already created
        if (mAdapter!=null) {
            mAdapter.setOnShortcutTapListener(listener);
        }
    }

    /**
     * Refresh the shortcut list
     * Not using the usual CursorAdapter refresh scheme because it requires a ContentProvider and I don't have one for the ShortcutDb
     */
    public void updateShortcutsList() {
        Log.d(TAG, "updateShortcutsList");
        getLoaderManager().restartLoader(0, (Bundle)null, this);
    }

    //
    // SambaDiscovery.Listener Implementation
    //

    @Override
    public void onDiscoveryStart() {
        // reset with empty list
        mAdapter.setAvailableSmbShares(new ArrayList<Share>(0));
    }

    @Override
    public void onDiscoveryEnd() {
        checkShortcutAvailability();
    }

    @Override
    public void onDiscoveryUpdate(List<Workgroup> workgroups) {
        List<Share> allShares = new ArrayList<Share>();
        for (Workgroup w : workgroups) {
            allShares.addAll(w.getShares());

        }
        mAdapter.setAvailableSmbShares(allShares);
    }

    @Override
    public void onDiscoveryFatalError() { checkShortcutAvailability(); }

    //
    // LoaderCallbacks Implementation
    //

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (DBG) Log.d(TAG, "onCreateLoader");
        return ShortcutDb.STATIC.getAllShortcutsCursorLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (DBG) Log.d(TAG, "onLoadFinished");
        mAdapter.changeCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (DBG) Log.d(TAG, "onLoaderReset");
        mAdapter.changeCursor(null);
    }

    private void checkShortcutAvailability(){

        if(mCheckShortcutAvailabilityTask!=null)
            mCheckShortcutAvailabilityTask.cancel(true);
        mCheckShortcutAvailabilityTask = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... arg0) {

                List<String> shares = mAdapter.getAllShares();


                for (int i = 0; i< mAdapter.getItemCount(); i++) {
                    Uri uri = mAdapter.getUri(i);
                    if (uri!=null&&uri.getScheme().equals("smb")&&uri.getPath()!=null
                            &&!uri.getPath().isEmpty()
                            &&!uri.getPath().equals("/")
                            &&(shares == null || !shares.contains(uri.getHost().toLowerCase()))
                            && FileEditorFactory.getFileEditorForUrl(uri, getActivity()).exists()) {
                        mAdapter.addAvailableShare(uri.getHost());
                    }

                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                mAdapter.notifyDataSetChanged();
            }
        }.execute();

    }
}
