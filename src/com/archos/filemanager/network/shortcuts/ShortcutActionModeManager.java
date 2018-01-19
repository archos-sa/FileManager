package com.archos.filemanager.network.shortcuts;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import com.archos.filemanager.R;
import com.archos.filemanager.ShortcutDb;
import com.archos.filemanager.dialog.ShortcutRenameDialog;
import com.archos.filemanager.network.shortcuts.ShortcutsAdapter.OnSelectionChangedListener;

/**
 * This class handles a selection of Shortcuts and display the related ActionMode items in a FragmentActivity
 * It is owned by ShortcutsFragment and only used by it
 * ShortcutActionModeManager only works along with a ShortcutsFragment instance
 */
public class ShortcutActionModeManager implements ActionMode.Callback, OnSelectionChangedListener {

    private static final String TAG = "ShortcutActionMode";
    private static final boolean DBG = false;

    final private ShortcutsFragment mFragment;
    final private ShortcutsAdapter mAdapter;

    private ActionMode mActionMode;

    public ShortcutActionModeManager(ShortcutsFragment frag, ShortcutsAdapter adapter) {
        mFragment = frag;
        mAdapter = adapter;
    }

    private void updateActionMode(){
        if(DBG) Log.d(TAG, "updateActionMode() "+this);
        if(getSelectedCount()>0){
            // Start or update the ActionMode
            if(mActionMode==null)
                mActionMode = mFragment.getActivity().startActionMode(this);
            else{
                mActionMode.invalidate();
            }
        }
        else if (mActionMode!=null) {
            // Selection is now null, stop the ActionMode
            mActionMode.finish();
            mActionMode = null;
        }
    }

    public void stopActionMode(){
        if(DBG) Log.d(TAG, "stopActionMode() "+this);
        if(mActionMode!=null) {
            mActionMode.finish();
            mActionMode = null;
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        menu.add(0, R.string.delete, 0, R.string.delete).setIcon(R.drawable.ic_menu_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, R.string.rename, 0, R.string.rename).setIcon(R.drawable.ic_menu_edit).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // Always allow to delete
        menu.findItem(R.string.delete).setVisible(true);
        // Allow to rename only when there is a single selection
        menu.findItem(R.string.rename).setVisible(getSelectedCount()==1);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()){
            case R.string.delete:
                boolean removeSuccess = false;
                for (final Long id : mAdapter.getSelectedShortcuts()) {
                    removeSuccess |= ShortcutDb.STATIC.removeShortcut(id); 
                    if (!removeSuccess) Log.e(TAG, "Failed to delete the shortcut "+id+" ! That should never happen!");
                }
                mFragment.updateShortcutsList();
                // I delay a bit the end of the ActionMode end animation to let a bit of time for the delete animation
                mFragment.getView().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        stopActionMode();
                    }
                }, 200);
                break;
            case R.string.rename:
                final Long shortcutId = mAdapter.getSelectedShortcuts().get(0);
                DialogFragment rd = new ShortcutRenameDialog();
                Bundle args = new Bundle();
                args.putLong(ShortcutRenameDialog.ARG_SHORTCUT_ID, shortcutId);
                rd.setArguments(args);
                rd.show(mFragment.getActivity().getSupportFragmentManager(), rd.getClass().getName());
                break;
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mAdapter.deselectAll();
        mActionMode=null;
    }

    @Override
    public void onSelectionChanged() {
        // update the action mode
        updateActionMode();
    }

    private int getSelectedCount() {
        return mAdapter.getSelectedShortcuts().size();
    }
}
