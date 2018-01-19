package com.archos.filemanager.network;


import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.StreamOverHttp;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.archos.filemanager.R;
import com.archos.filemanager.ShortcutDb;
import com.archos.filemanager.listing.ListingFragment;
import com.archos.filemanager.network.ServerCredentialsDialog.onConnectClickListener;
import com.archos.filemanager.network.ServerCredentialsDialog.onDismissListener;

import java.io.IOException;

public class NetworkListingFragment extends ListingFragment {

	/**
	 * android.net.Uri to start with
	 */
	public static final String KEY_STARTING_URI = "STARTING_URI";

	/**
	 * android.net.Uri used as root.
	 * goBackOneLevel() will return false if this root Uri is the current one
	 * rootUri MUST be a parent of startingUri (or equal to startingUri)
	 */
	public static final String KEY_ROOT_URI = "ROOT_URI";

	/**
	 * Name to be displayed for the root level
	 */
	public static final String KEY_ROOT_NAME = "ROOT_NAME";

	@Override
	protected Uri getStartingUri() {
		Uri uri = null;
		if (getArguments()!=null) {
			uri = getArguments().getParcelable(KEY_STARTING_URI);
		}

		if (uri==null) {
			throw new IllegalStateException("KEY_STARTING_URI Uri is mandatory in the fragment arguments!");
		}
		return uri;
	}

	@Override
	protected Uri getRootUri() {
		Uri uri = null;
		if (getArguments()!=null) {
			uri = getArguments().getParcelable(KEY_ROOT_URI);
		}

		if (uri==null) {
			throw new IllegalStateException("KEY_ROOT_URI Uri is mandatory in the fragment arguments!");
		}
		return uri;
	}

	@Override
	protected String getRootName(Context context) {
		String name = null;
		if (getArguments()!=null) {
			name = getArguments().getString(KEY_ROOT_NAME);
		}

		if (name==null) {
			throw new IllegalStateException("KEY_ROOT_NAME String is mandatory in the fragment arguments!");
		}
		return name;
	}

	@Override
	protected void updateMenu() {
		super.updateMenu();
		getActivity().invalidateOptionsMenu();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
        if(isActive()) { // do not do anything if not focused fragment
            menu.add(Menu.NONE, R.string.add_to_shortcuts, 20, R.string.add_to_shortcuts).setIcon(R.drawable.ic_menu_shortcut_create).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, R.string.remove_from_shortcuts, 20, R.string.remove_from_shortcuts).setIcon(R.drawable.ic_menu_shortcut_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
        if(isActive()) { // do not do anything if not focused fragment
            if (getCurrentUri() != null) {
                boolean currentUriIsShortcut = ShortcutDb.STATIC.containShortcut(getCurrentUri());
                menu.findItem(R.string.add_to_shortcuts).setVisible(!currentUriIsShortcut);
                menu.findItem(R.string.remove_from_shortcuts).setVisible(currentUriIsShortcut);
            } else {
                menu.findItem(R.string.add_to_shortcuts).setVisible(false);
                menu.findItem(R.string.remove_from_shortcuts).setVisible(false);
            }
        }
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        if (isActive()) { // do not do anything if not focused fragment
		switch(item.getItemId()) {
                case R.string.add_to_shortcuts:
                    Uri uri = getCurrentUri();
                    String name = uri.getLastPathSegment();
                    if (name == null) {
                        name = uri.getHost(); // fall back to host name if it is all we have
                    }
                    boolean success = ShortcutDb.STATIC.insertShortcut(uri, name);
                    if (success) {
                        Toast.makeText(getActivity(), getString(R.string.shortcut_created, name), Toast.LENGTH_SHORT).show();
                    }
                    // Update the shortcut list
                    updateShortcutListInNetworkRoot();
                    // Update the action menu
                    getActivity().invalidateOptionsMenu();
                    return true;
                case R.string.remove_from_shortcuts:
                    int number = ShortcutDb.STATIC.removeShortcut(getCurrentUri());
                    String msg = null;
                    if (number == 1) {
                        msg = getString(R.string.one_shortcut_removed);
                    } else if (number > 1) {
                        msg = getString(R.string.n_shortcut_removed, number);
                    }
                    if (msg != null) {
                        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
                    }
                    // Update the shortcut list
                    updateShortcutListInNetworkRoot();
                    // Update the action menu
                    getActivity().invalidateOptionsMenu();
                    return true;
            }
        }
        return super.onOptionsItemSelected(item);
	}
    @Override
    protected long getListingTimeOut() {
        return 10000;
    }

	/**
	 * Ugly way to update the shortcut list in NetworkRootFragment...
	 * Problem is that I don't want to implement a ContentProvider in ShortcutDb, and it is required to have the nice cursor/listview update scheme working.
	 */
	private void updateShortcutListInNetworkRoot() {
		if (getParentFragment() instanceof NetworkRootFragment) {
			((NetworkRootFragment)getParentFragment()).updateShortcutsList();
		}
		else {
			throw new IllegalStateException("Sorry developer, looks like the ugly code used to update the shortcut list is not working as expected... You should take a look at NetworkListingFragment.java");
		}
	}

    @Override
    protected boolean onFileClick(MetaFile2 file, View v) {
        if (!super.onFileClick(file, v) && file.isFile()) {
            // play through http-tunnel or propose to download
            String mimeType = file.getMimeType();
            if (!isMediaFile(mimeType)) {
                showDownloadDialog(file);
                return true;
            }
            Uri contentUri;
            try {
                StreamOverHttp stream = new StreamOverHttp(file.getUri(), mimeType);
                contentUri = stream.getUri(file.getUri().getLastPathSegment());
            } catch (IOException e) {
                contentUri = file.getUri();
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.d(TAG, "data=" + contentUri);
            Log.d(TAG, "type=" + mimeType);
            intent.setDataAndType(contentUri, mimeType);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                showDownloadDialog(file);
            }
            return true;
        }
        return false;
    }

    private boolean isMediaFile(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }
        if (mimeType.startsWith("video") || mimeType.startsWith("audio")/* || mimeType.startsWith("image")*/) {
            return true;
        }
        return false;
    }

    private void showDownloadDialog(MetaFile2 file) {
        DownloadDialog downloadDialog = new DownloadDialog();
        Bundle args = new Bundle();
        args.putSerializable(DownloadDialog.METAFILE, file);
        downloadDialog.setArguments(args);
        downloadDialog.show(getActivity().getSupportFragmentManager(), DownloadDialog.class.getCanonicalName());
    }

    private void askForCredentials() {
        Bundle args = new Bundle();
        Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mCurrentUri.toString());
        if (cred != null) {
            args.putString(ServerCredentialsDialog.USERNAME, "");
            args.putString(ServerCredentialsDialog.PASSWORD, "");
        }
        args.putInt(ServerCredentialsDialog.PORT, mCurrentUri.getPort());
        int type = -1;
        if (mCurrentUri.getScheme().equalsIgnoreCase("ftp")) {
            type = ServerCredentialsDialog.TYPE_FTP;
        } else if (mCurrentUri.getScheme().equalsIgnoreCase("sftp")) {
            type = ServerCredentialsDialog.TYPE_SFTP;
        } else if (mCurrentUri.getScheme().equalsIgnoreCase("ftps")) {
            type = ServerCredentialsDialog.TYPE_FTPS;
        } else if (mCurrentUri.getScheme().equalsIgnoreCase("smb")) {
            type = ServerCredentialsDialog.TYPE_SMB;
        }
        args.putInt(ServerCredentialsDialog.TYPE, type);
        args.putString(ServerCredentialsDialog.REMOTE, mCurrentUri.getHost());
        args.putString(ServerCredentialsDialog.PATH, mCurrentUri.getPath());
        args.putInt(ServerCredentialsDialog.FOCUS_ON, ServerCredentialsDialog.FOCUS_USERNAME);
        ServerCredentialsDialog dialog = new ServerCredentialsDialog();

        dialog.setOnConnectClickListener(new onConnectClickListener() {
            @Override
            public void onConnectClick(Uri uri) {
                mCurrentUri = uri;
                refresh();
            }
        });
        dialog.setOnDismissListener(new onDismissListener() {
            @Override
            public void dismiss() {
                NetworkListingFragment.this.getActivity().onBackPressed();
            }
        });

        dialog.setArguments(args);
        dialog.show(getFragmentManager(), dialog.getClass().getName());
    }

    @Override
    public void onListingFatalError(Exception e, ListingEngine.ErrorEnum error) {
        if (error == ListingEngine.ErrorEnum.ERROR_NO_PERMISSION) {
            askForCredentials();
        } else {
            super.onListingFatalError(e, error);
        }
    }

    @Override
    public void onListingFileInfoUpdate(Uri uri, MetaFile2 metaFile2) {

    }

    @Override
	public void onCredentialRequired(Exception e) {
        //launch authentification dialog
        askForCredentials();
	}

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }
}
