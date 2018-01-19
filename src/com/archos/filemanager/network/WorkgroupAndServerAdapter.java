package com.archos.filemanager.network;

import java.util.ArrayList;
import java.util.List;

import com.archos.filecorelibrary.samba.Share;
import com.archos.filecorelibrary.samba.Workgroup;
import com.archos.filemanager.R;
import com.archos.filemanager.network.SambaDiscoveryFragment.OnShareOpenListener;

import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


public class WorkgroupAndServerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "WorkgroupAndServerAdapter";

    private static final int TYPE_WORKGROUP_SEPARATOR = 0;
    private static final int TYPE_SHARE = 1;

    private ArrayList<Workgroup> mWorkgroups;
    private ArrayList<Share> mShares;
    private ArrayList<Integer> mSeparatorIndexes;

    private boolean mDisplayWorkgroupSeparator;

    OnShareOpenListener mOnShareOpenListener;

    public class ShareViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIcon;
        private final TextView mMainTv;
        private final TextView mSecondaryTv;

        public ShareViewHolder(View v) {
            super(v);
            v.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mOnShareOpenListener.onShareOpen(mShares.get(getAdapterPosition()));
                }
            });
            mIcon = (ImageView) v.findViewById(R.id.icon);
            mMainTv = (TextView) v.findViewById(R.id.name);
            mSecondaryTv = (TextView) v.findViewById(R.id.info);
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
    }

    public static class WorkgroupSeparatorViewHolder extends RecyclerView.ViewHolder {
        private final TextView mName;

        public WorkgroupSeparatorViewHolder(View v) {
            super(v);
            // Define click listener for the ViewHolder's View.
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "Element " + getAdapterPosition() + " clicked.");
                }
            });
            mName = (TextView) v.findViewById(R.id.name);
        }
        public TextView getNameTextView() {
            return mName;
        }
    }

    public WorkgroupAndServerAdapter() {
        setHasStableIds(false);
        mWorkgroups = new ArrayList<Workgroup>(); // init with empty list for convenience
        mShares = new ArrayList<Share>();
        mSeparatorIndexes = new ArrayList<Integer>();
    }

    public void setOnShareOpenListener(OnShareOpenListener listener) {
        mOnShareOpenListener = listener;
    }

    /**
     * Store the list (and possibly other things) in a bundle
     * @param outState
     */
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList("mWorkgroups", mWorkgroups);
        outState.putParcelableArrayList("mShares", mShares);
        outState.putSerializable("mSeparatorIndexes", mSeparatorIndexes);
        outState.putBoolean("mDisplayWorkgroupSeparator", mDisplayWorkgroupSeparator);
    }

    /**
     * Restore the state that has been saved in a bundle
     * @param inState
     */
    @SuppressWarnings("unchecked")
    public void onRestoreInstanceState(Bundle inState) {
        mWorkgroups = inState.getParcelableArrayList("mWorkgroups");
        mShares = inState.getParcelableArrayList("mShares");
        mSeparatorIndexes = (ArrayList<Integer>)inState.getSerializable("mSeparatorIndexes");
        mDisplayWorkgroupSeparator = inState.getBoolean("mDisplayWorkgroupSeparator");
    }

    public void updateData(List<Workgroup> workgroups) {
        // convert to ArrayList if needed (we need ArrayList to serialize)
        if (workgroups instanceof ArrayList<?>) {
            mWorkgroups = (ArrayList<Workgroup>)workgroups;
        } else {
            mWorkgroups.clear();
            mWorkgroups.addAll(workgroups);
        }

        // No need to display workgroup if there is only one 
        mDisplayWorkgroupSeparator = mWorkgroups.size()>1;

        mShares.clear();
        mSeparatorIndexes.clear();

        // Build the concatenated share list and the index of separators
        int i = 0;
        for (Workgroup w : mWorkgroups) {
            if (mDisplayWorkgroupSeparator) {
                mSeparatorIndexes.add(Integer.valueOf(i));
                i++; // separator count
                // TRICK: add a null share at each separator "position". Will save us from handling the offsets.
                mShares.add(null);
                i+=w.getShares().size(); // shares count
            }
            // Add the actual shares
            mShares.addAll(w.getShares());
        }

        notifyDataSetChanged();
    }

    public void clearData() {
        updateData(new ArrayList<Workgroup>());
    }

    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view.
        if (viewType == TYPE_SHARE) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_file, viewGroup, false);
            v.findViewById(R.id.select).setVisibility(View.GONE);
            return new ShareViewHolder(v);
        }
        else if (viewType == TYPE_WORKGROUP_SEPARATOR) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_workgroup_separator, viewGroup, false);
            return new WorkgroupSeparatorViewHolder(v);
        }
        else {
            throw new IllegalArgumentException("invalid viewType "+viewType);   
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position) {
        if (viewHolder.getItemViewType() == TYPE_SHARE) {
            final Share share = mShares.get(position); // no separator offset to handle here! see "trick" comment in the source code above for explanation
            ShareViewHolder shareViewHolder = (ShareViewHolder)viewHolder;
            shareViewHolder.getIcon().setImageResource(R.drawable.filetype_nas);
            shareViewHolder.getMainTextView().setText(share.getDisplayName());
            shareViewHolder.getSecondaryTextView().setVisibility(View.GONE);
        }
        else if (viewHolder.getItemViewType() == TYPE_WORKGROUP_SEPARATOR) {
            WorkgroupSeparatorViewHolder wsViewHolder = (WorkgroupSeparatorViewHolder)viewHolder;
            // Check which workgroup it is
            int workgroupIndex = mSeparatorIndexes.indexOf(Integer.valueOf(position));
            Workgroup workgroup = mWorkgroups.get(workgroupIndex);
            wsViewHolder.getNameTextView().setText(workgroup.getName());
        }
        else {
            throw new IllegalArgumentException("invalid viewType "+viewHolder.getItemViewType());   
        }
    }

    @Override
    public int getItemCount() {
        return mShares.size(); // HACK: there is a null share item at each workgroup position, hence the mShares list size is the whole size
    }

    @Override
    public int getItemViewType(int position) {
        if (mSeparatorIndexes.contains(Integer.valueOf(position))) {
            return TYPE_WORKGROUP_SEPARATOR;
        }
        else {
            return TYPE_SHARE;
        }
    }
}
