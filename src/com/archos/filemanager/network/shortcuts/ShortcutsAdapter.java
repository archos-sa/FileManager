package com.archos.filemanager.network.shortcuts;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.archos.filecorelibrary.samba.Share;
import com.archos.filemanager.ArchosCheckBox;
import com.archos.filemanager.CursorRecyclerAdapter;
import com.archos.filemanager.R;
import com.archos.filemanager.ShortcutDb;
import com.archos.filemanager.network.shortcuts.ShortcutsFragment.OnShortcutTapListener;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ShortcutsAdapter extends CursorRecyclerAdapter<ShortcutsAdapter.ViewHolder> {

    private static final String TAG = "ShortcutsAdapter";

    public interface OnSelectionChangedListener{
        public void onSelectionChanged();
    }

    private OnShortcutTapListener mOnShortcutTapListener;
    private OnSelectionChangedListener mOnSelectionChangedListener;

    private LinkedList<Long> mSelectedShortcuts = new LinkedList<Long>();

    private ArrayList<String> mAvailableSmbShares = new ArrayList<String>(0);

    private int idColumnIndex;
    private int uriColumnIndex;
    private int shortcutNameIndex;

    public ShortcutsAdapter() {
        super(null);
    }

    public void setOnShortcutTapListener(OnShortcutTapListener listener) {
        mOnShortcutTapListener = listener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener){
        mOnSelectionChangedListener = listener;
    }

    /**
     * Store the state in a bundle
     * @param outState
     */
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("mAvailableSmbShares", mAvailableSmbShares);
    }

    /**
     * Restore the state that has been saved in a bundle
     * @param inState
     */
    @SuppressWarnings("unchecked")
    public void onRestoreInstanceState(Bundle inState) {
        if (inState!=null) {
            mAvailableSmbShares = (ArrayList<String>)inState.getSerializable("mAvailableSmbShares");
        }
    }

    private void updateIndexes(Cursor c) {
        idColumnIndex = c.getColumnIndex(BaseColumns._ID);
        uriColumnIndex = c.getColumnIndex(ShortcutDb.KEY_URI);
        shortcutNameIndex = c.getColumnIndex(ShortcutDb.KEY_SHORTCUT_NAME);
    }


    public class ViewHolder extends RecyclerView.ViewHolder {
        private final View mRoot;
        private final ImageView mIcon;
        private final TextView mMainTv;
        private final TextView mSecondaryTv;
        private final ArchosCheckBox mCheckbox;
        private Uri mUri;
        private boolean mAvailable;

        public ViewHolder(View v) {
            super(v);
            // Define click listener for the ViewHolder's View.
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Element " + mUri + " clicked.");
                    if (mAvailable) {
                        mOnShortcutTapListener.onShortcutTap(mUri);
                    } else {
                        mOnShortcutTapListener.onUnavailableShortcutTap(mUri);
                    }
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    updateItem(getAdapterPosition(), mCheckbox);
                    return true;
                }
            });
            mRoot = v;
            mIcon = (ImageView) v.findViewById(R.id.icon);
            mIcon.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateItem(getAdapterPosition(), mCheckbox);
                }
            });
            mIcon.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    updateItem(getAdapterPosition(), mCheckbox);
                    return true;
                }
            });
            mMainTv = (TextView) v.findViewById(R.id.name);
            mSecondaryTv = (TextView) v.findViewById(R.id.info);
            mCheckbox = (ArchosCheckBox) v.findViewById(R.id.select);
            mCheckbox.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    updateItem(getAdapterPosition(), mCheckbox);
                }
            });
        }
        public View getRoot() {
            return mRoot;
        }
        public ImageView getIcon() {
            return mIcon;
        }
        public TextView getMainTextView() {
            return mMainTv;
        }
        public TextView getSecondaryTextView() {
            return mSecondaryTv;
        }
        public ArchosCheckBox getCheckbox() {
            return mCheckbox;
        }
        public void setUri(Uri uri) {
            mUri = uri;
        }
        public void setAvailable(boolean available) {
            mAvailable = available;
            // Do not change the root alpha because it is also modified by the RecyclerView
            final float alpha = available ? 1.0f : 0.3f;
            mIcon.setAlpha(alpha);
            mMainTv.setAlpha(alpha);
            mSecondaryTv.setAlpha(alpha);
        }
    }

    private void updateItem(int position, ArchosCheckBox checkbox){
        Long id = Long.valueOf(getItemId(position));

        int previousSelectedCount = mSelectedShortcuts.size();

        boolean added;

        // Add or remove from the selection list
        if(!mSelectedShortcuts.remove(id)){
            mSelectedShortcuts.add(id);
            added=true;
        } else {
            added=false;
        }

        int newSelectedCount = mSelectedShortcuts.size();

        // If first selection or if no more selection, all the items are modified to show or hide all the checkboxes
        if (previousSelectedCount==0 || newSelectedCount==0) {
            notifyItemRangeChanged(0, getItemCount());
        }
        else {
            // only the clicked item need to be updated, we do it "by hand" to have nicer animation
            checkbox.setChecked(added);
        }

        // Tell the world (to update the ActionMode actually)
        mOnSelectionChangedListener.onSelectionChanged();
    }

    /**
     * Allow to set which SMB shortcuts have actually available targets
     * @param shares
     */
    public void setAvailableSmbShares(List<Share> shares) {

        // We're converting to strings because we are only interested in the names of the shares and it will be easier to call mAvailableShares.contains()
        for (Share s : shares) {
           if (!mAvailableSmbShares.contains(s.getName())) {
               mAvailableSmbShares.add(s.getName());
           }
            String ip = Uri.parse(s.getAddress()).getHost();//retrieve ip address from smb://ip/
            if (!mAvailableSmbShares.contains(ip)) {
                mAvailableSmbShares.add(ip);
            }
        }
        this.notifyItemRangeChanged(0, getItemCount());
    }
    public void addAvailableShare(String s) {
        mAvailableSmbShares.add(s);
    }
    public ArrayList<String> getAllShares(){
        return mAvailableSmbShares;
    }
    @Override
    public void changeCursor(Cursor cursor) {
        if (cursor!=null) {
            updateIndexes(cursor);
        }
        super.changeCursor(cursor);
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor!=null) {
            updateIndexes(newCursor);
        }
        return super.swapCursor(newCursor);
    }

    @Override
    public void onBindViewHolderCursor(ShortcutsAdapter.ViewHolder holder, Cursor cursor) {
        Uri uri = Uri.parse(cursor.getString(uriColumnIndex));
        int iconResId = R.drawable.filetype_shortcut; //default
        if ("smb".equals(uri.getScheme())) {
            iconResId = R.drawable.filetype_shortcut_smb;
        } else if ("ftp".equals(uri.getScheme())) {
            iconResId = R.drawable.filetype_shortcut_ftp;
        } else if ("sftp".equals(uri.getScheme())) {
            iconResId = R.drawable.filetype_shortcut_sftp;
        }
        holder.getIcon().setImageResource(iconResId);
        holder.getMainTextView().setText(cursor.getString(shortcutNameIndex));
        holder.getSecondaryTextView().setText(uri.getHost()+uri.getPath());
        holder.setUri(uri);

        // Show checkboxes if there is at least one selection + check the selected items
        boolean isSelected = mSelectedShortcuts.contains(Long.valueOf(cursor.getLong(idColumnIndex)));
        holder.getCheckbox().setCheckedNoAnimation(isSelected);
        holder.getCheckbox().setVisibility(mSelectedShortcuts.size()>0 ? View.VISIBLE : View.GONE);

        // Set SMB share availability
        boolean available;
        if ("smb".equals(uri.getScheme())) {
            available = mAvailableSmbShares.contains(uri.getHost());
        } else {
            available = true;
        }
        holder.setAvailable(available);
    }

    @Override
    public ShortcutsAdapter.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_file, viewGroup, false);
        ShortcutsAdapter.ViewHolder vh= new ViewHolder(v);
        return vh;
    }

    /**
     * @return the list of DB id of the selected shortcuts
     */
    public List<Long> getSelectedShortcuts() {
        return mSelectedShortcuts;
    }

    public Uri getUri(int position){
        getCursor().moveToPosition(position);
        return Uri.parse(getCursor().getString(uriColumnIndex));
    }

    public void deselectAll() {
        mSelectedShortcuts.clear();
        notifyItemRangeChanged(0, getItemCount());
    }
}
