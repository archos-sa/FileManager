package com.archos.filemanager.network;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filemanager.ArchosCheckBox;
import com.archos.filemanager.R;

import java.util.List;

/**
 * Created by alexandre on 14/04/15.
 */
public class CredentialsManagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<NetworkCredentialsDatabase.Credential> mCredentialList;
    private final Context mContext;
    private final OnItemClickListener mOnItemClickListener;
    private final OnItemLongClickListener mOnItemLongClickListener;

    public void setCredentials(List<NetworkCredentialsDatabase.Credential> credentials) {
        mCredentialList = credentials;
    }

    public interface OnItemClickListener{
        public void onItemClick(NetworkCredentialsDatabase.Credential credential);
    }
    public interface OnItemLongClickListener{
        public boolean onItemLongClick(NetworkCredentialsDatabase.Credential credential);
    }
    public class CredentialViewHolder extends RecyclerView.ViewHolder {

        private final TextView mMainTv;
        private final TextView mSecondaryTv;
        private final View mRoot;

        public CredentialViewHolder(View v) {
            super(v);
            mRoot = v;
            // Define click listener for the ViewHolder's View.
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mOnItemClickListener.onItemClick(mCredentialList.get(getAdapterPosition()));
                }
            });
            v.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    if (mOnItemLongClickListener != null)
                        return mOnItemLongClickListener.onItemLongClick(mCredentialList.get(getAdapterPosition()));
                    return false;
                }
            });
            mMainTv = (TextView) v.findViewById(R.id.main);
            mSecondaryTv = (TextView) v.findViewById(R.id.secondary);
        }
        public View getRoot() {
            return mRoot;
        }
        public TextView getMainTextView() {
            return mMainTv;
        }
        public TextView getSecondaryTextView() {
            return mSecondaryTv;
        }

    }
    public CredentialsManagerAdapter(List<NetworkCredentialsDatabase.Credential> credentialList, Context context, OnItemClickListener onItemClickListener, OnItemLongClickListener onItemLongClickListener){
        mCredentialList = credentialList;
        mContext = context;
        mOnItemClickListener = onItemClickListener;
        mOnItemLongClickListener = onItemLongClickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_credential, viewGroup, false);
        return new CredentialViewHolder(v);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        ((CredentialViewHolder)viewHolder).getMainTextView().setText(mCredentialList.get(i).getUriString());
        ((CredentialViewHolder)viewHolder).getSecondaryTextView().setText(mCredentialList.get(i).getUsername());
    }

    @Override
    public int getItemCount() {
        return mCredentialList.size();
    }
}
