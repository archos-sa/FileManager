package com.archos.filemanager.listing;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ShareActionProvider;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.zip.ZipUtils;
import com.archos.filemanager.FileManagerService;
import com.archos.filemanager.InfoDialogFragment;
import com.archos.filemanager.PasteAndDragBin;
import com.archos.filemanager.R;
import com.archos.filemanager.dialog.FileRenameDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * This class handles a selection of MetaFile2 and display the related ActionMode items in a FragmentActivity
 * Each ListingFragment has its own MetaFileActionModeManager instance
 * MetaFileActionModeManager only works along with a ListingFragment instance
 */
public class MetaFileActionModeManager implements ActionMode.Callback, FileAdapter.OnSelectionChangedListener {

    private static final String TAG = "MetaFileActionMode";
    private static final boolean DBG = false;

    final private FragmentActivity mActivity;
    final private FileAdapter mFileAdapter;
    final private ListingFragment mListingFragment;
    private ActionMode mActionMode;

    public MetaFileActionModeManager(FileAdapter fileAdapter,ListingFragment listingFragment){
        mFileAdapter = fileAdapter;
        mListingFragment = listingFragment;
        mActivity = listingFragment.getActivity();
    }

    public void updateVisibility(){
        if(DBG) Log.d(TAG, "show() "+this);

        if(mFileAdapter.getSelectedFiles().size()>0&&mListingFragment.isActive()){
            // Start or update the ActionMode
            if(mActionMode==null) {
                mActionMode = mActivity.startActionMode(this);
                mActionMode.invalidate();
            }
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

    public void hide(){
        if(DBG) Log.d(TAG, "hide() "+this);
        if(mActionMode!=null) {
            mActionMode.finish();
            mActionMode = null;
        }
    }

    /**
     * returns true if the ActionMode is currently showing
     */
    public boolean isShowing() {
        return (mActionMode!=null);
    }

    private boolean hasFolder(List<MetaFile2> selectedItems){
        for(MetaFile2 mf : selectedItems)
            if(mf.isDirectory()) return true;
        return false;
    }

    private boolean canWrite(List<MetaFile2> selectedItems){
        for(MetaFile2 mf : selectedItems){
            if(!mf.canWrite())
                return false;
        }
        return true;
    }

    private boolean canRead(List<MetaFile2> selectedItems){
        for(MetaFile2 mf : selectedItems){
            if(!mf.canRead())
                return false;
        }
        return true;
    }

    private void addShareSubMenu(Menu menu, List<MetaFile2> selectedFiles) {
        MenuItem shareItem = menu.findItem(R.string.share);
        if (shareItem != null) {
            // Set an ActionProvider on this item
            ShareActionProvider shareActionProvider = new ShareActionProvider(mActivity);
            shareItem.setActionProvider(shareActionProvider).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            // Build the intent which will be sent when trying to share this file or selection
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (selectedFiles.isEmpty()) {
                return;
            }

            // Several files selected => add the uris of all the files
            ArrayList<Uri> uris = new ArrayList<Uri>(selectedFiles.size());
            String mimeType = null;
            // See the doc about Intent.ACTION_SEND_MULTIPLE for MIME type explanations.
            for (MetaFile2 f : selectedFiles) {
                if (f.isFile()) {
                    String tempMimeType = f.getMimeType();
                    if (mimeType == null) {
                        mimeType = tempMimeType;
                    } else if (tempMimeType!=null&!mimeType.equals(tempMimeType) && !mimeType.equals("*/*")) {
                        int posMT = mimeType.indexOf('/');
                        int posTMT = tempMimeType.indexOf('/');
                        if (posMT != posTMT) {
                            mimeType = "*/*";
                        } else if (mimeType.substring(0, posMT).equals(
                                tempMimeType.substring(0, posTMT))) {
                            mimeType = mimeType.substring(0, posMT + 1) + '*';
                        } else {
                            mimeType = "*/*";
                        }
                    }
                    uris.add(f.getUri());
                }
            }
            if (mimeType == null) {
                mimeType = "*/*";
            }

            shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uris);
            shareActionProvider.setShareIntent(shareIntent);
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        menu.clear();
        /*
         * Copy : always
         * Paste : if something to copy and rights on current directory
         * delete : if rights on every selected files
         * share : if no folder
         * info : always
         * zip ?
         * select all
         * deselect not needed anymore
         * rename : if one and write ok
         */
        menu.addSubMenu(0, R.string.share, 0, R.string.share);
        menu.add(0, R.string.details, 0, R.string.details).setIcon(R.drawable.ic_menu_info).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, R.string.delete, 0, R.string.delete).setIcon(R.drawable.ic_menu_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, android.R.string.cut, 0, android.R.string.cut).setIcon(R.drawable.ic_menu_file_cut).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, android.R.string.copy, 0, android.R.string.copy).setIcon(R.drawable.ic_menu_file_copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, R.string.rename, 0, R.string.rename).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, R.string.zip_compress_action, 9, R.string.zip_compress_action).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, R.string.add_to_launcher, 9, R.string.add_to_launcher).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, R.string.zip_extract_action, 10, R.string.zip_extract_action).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, android.R.string.paste, Menu.NONE, android.R.string.paste).setIcon(R.drawable.ic_menu_file_paste);
        menu.add(0, android.R.string.selectAll, 5, android.R.string.selectAll).setIcon(R.drawable.ic_menu_select_all);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        boolean displayShareMenu;
        boolean displayDeleteMenu;
        boolean displayCopyMenu;
        boolean displayCutMenu;
        boolean displayPasteMenu;
        boolean displayZipMenu;
        boolean displaySelectAllMenu;
        boolean displayExtractMenu = true;
        boolean displayRenameMenu;
        boolean displayAddToHome;
        List<MetaFile2> selectedItems = mFileAdapter.getSelectedFiles();

        displayZipMenu = !ZipUtils.isZipMetaFile(mListingFragment.getCurrentMetaFile());
        displayShareMenu= !hasFolder(selectedItems);
        displayDeleteMenu = canWrite(selectedItems);
        displayCopyMenu = canRead(selectedItems);
        displayCutMenu = canWrite(selectedItems);
        displayPasteMenu = PasteAndDragBin.getPastebinMetafiles().size()>0&&(mListingFragment.getCurrentMetaFile()==null
                || mListingFragment.getCurrentMetaFile().canWrite()
                )&&!ZipUtils.isZipMetaFile(mListingFragment.getCurrentMetaFile()); //do not paste in zip files. Write can be true because the file itself can be modified
        displayRenameMenu = selectedItems.size()==1&& canWrite(selectedItems);

        displaySelectAllMenu = mFileAdapter.getSelectedFiles().size()< mFileAdapter.getFiles().size();
        displayAddToHome = true;
        for(MetaFile2 mf2 : selectedItems) {
            if(mf2.isFile()&&mf2.isRemote())
                displayAddToHome = false;
            if (!"application/zip".equals(mf2.getMimeType()))
                displayExtractMenu = false;
        }
        if(displayShareMenu)
            addShareSubMenu(menu,selectedItems);
        menu.findItem(R.string.share).setVisible(displayShareMenu);
        menu.findItem(R.string.delete).setVisible(displayDeleteMenu);
        menu.findItem(android.R.string.cut).setVisible(displayCutMenu);
        menu.findItem(android.R.string.copy).setVisible(displayCopyMenu);
        menu.findItem(R.string.rename).setVisible(displayRenameMenu);
        menu.findItem(R.string.zip_compress_action).setVisible(displayZipMenu);
        menu.findItem(R.string.zip_extract_action).setVisible(displayExtractMenu);
        menu.findItem(android.R.string.paste).setVisible(displayPasteMenu);
        menu.findItem(android.R.string.selectAll).setVisible(displaySelectAllMenu);
        menu.findItem(R.string.add_to_launcher).setVisible(displayAddToHome);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()){
            case R.string.zip_compress_action:
                FileManagerService.fileManagerService.compress(mFileAdapter.getSelectedFiles(),Uri.withAppendedPath(mListingFragment.getCurrentUri(),mFileAdapter.getSelectedFiles().get(0).getNameWithoutExtension()+".zip"));
                break;
            case R.string.zip_extract_action:
                FileManagerService.fileManagerService.extract(mFileAdapter.getSelectedFiles(), mListingFragment.getCurrentUri());
                break;
            case R.string.add_to_launcher:
                for(MetaFile2 metaFile2: mFileAdapter.getSelectedFiles()){
                    HomeShortcutBuilder.createShortcutForMetaFile(metaFile2);
                }
                break;
            case android.R.string.copy:
                PasteAndDragBin.addToPastebin(mFileAdapter.getSelectedFiles(), FileManagerService.FileActionEnum.COPY);
                mActionMode.invalidate();
                mActivity.invalidateOptionsMenu();
                break;
            case android.R.string.cut:
                PasteAndDragBin.addToPastebin(mFileAdapter.getSelectedFiles(), FileManagerService.FileActionEnum.CUT);
                mActionMode.invalidate();
                mActivity.invalidateOptionsMenu();
                break;
            case android.R.string.paste:
                if(PasteAndDragBin.getPastebinMetafiles().size()>0){
                    Uri target = mListingFragment.getCurrentUri();
                    if(FileManagerService.fileManagerService!=null){
                        List<MetaFile2> tmp = new ArrayList<>(PasteAndDragBin.getPastebinMetafiles());
                        if(PasteAndDragBin.currentPasteMode == FileManagerService.FileActionEnum.COPY)
                            FileManagerService.fileManagerService.copy(PasteAndDragBin.getPastebinMetafiles(), target);
                        else {
                            if(FileManagerService.fileManagerService.cut(tmp, target))
                                PasteAndDragBin.clearPastebin();
                        }
                    }
                }
                break;
            case R.string.delete:
                askDeleteConfirm(mFileAdapter.getSelectedFiles());

                break;
            case R.string.details:
                Fragment f = new InfoDialogFragment();
                Bundle args = new Bundle();
                args.putSerializable(InfoDialogFragment.TAG_FILE_LIST, getSelectedFiles());
                f.setArguments(args);
                mActivity.getSupportFragmentManager().beginTransaction().add(f, "info").commit();
                break;
            case android.R.string.selectAll:
                mFileAdapter.selectAll();
                break;
            case R.string.rename:
                DialogFragment rd = new FileRenameDialog();
                Bundle renameArgs = new Bundle();
                renameArgs.putSerializable(FileRenameDialog.ARG_METAFILE, getSelectedFiles().get(0));
                renameArgs.putParcelable(ListingFragment.URI_TO_REFRESH, mListingFragment.getCurrentUri());
                rd.setArguments(renameArgs);
                rd.show(mActivity.getSupportFragmentManager(), rd.getClass().getName());
                break;
        }
        return true;
    }

    private void askDeleteConfirm(final List<MetaFile2> selectedFiles) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setMessage(selectedFiles.size()>1?R.string.confirm_delete_many:R.string.confirm_delete_one);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if(FileManagerService.fileManagerService!=null){
                    FileManagerService.fileManagerService.delete(selectedFiles);
                }
            }
        })
        .setNegativeButton(android.R.string.cancel, null).show();
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        if(DBG) Log.d(TAG, "onDestroyActionMode "+this);
        mActionMode=null; // must be before deselect all
        // If the ActionMode is stopped and the fragment is not active, it means user switched to another fragment. In that case
        // We do not reset the selection, because user may want to come back to it
        if(mListingFragment.isActive()) {
            // ActionMode is stopped in the fragment onDestroy, but it must not reset the
            // selection in case of a screen rotation for example
            if (!mActivity.isChangingConfigurations()) {
                mFileAdapter.deselectAll();
            }
        }
        
    }

    //implements FileAdapter.OnSelectionChangedListener
    @Override
    public void onSelectionChanged(boolean stateChanged, boolean select) {
        updateVisibility();
    }

    /**
     * Return an ArrayList of MetaFiles2
     * @return
     */
    private ArrayList<MetaFile2> getSelectedFiles() {
        List<MetaFile2> list = mFileAdapter.getSelectedFiles();
        if (list instanceof ArrayList<?>) {
            // Already an ArrayList, just return it
            return (ArrayList<MetaFile2>)list;
        }
        else {
            // convert to an ArrayList
            return new ArrayList<MetaFile2>(list);
        }
    }

}
