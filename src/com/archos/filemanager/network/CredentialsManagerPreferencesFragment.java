package com.archos.filemanager.network;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filemanager.listing.DividerItemDecoration;

import java.util.List;

public class CredentialsManagerPreferencesFragment extends Fragment implements CredentialsManagerAdapter.OnItemClickListener, CredentialsManagerAdapter.OnItemLongClickListener {

    private RecyclerView mList;
    private View mEmptyView;
    private TextView mEmptyTextView;
    private LinearLayoutManager mLayoutManager;
    private List<NetworkCredentialsDatabase.Credential> mCredentials;
    private CredentialsManagerAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }
    @Override
    public View onCreateView(LayoutInflater inflater,ViewGroup container, Bundle savedInstanceState){
        View v = inflater.inflate(com.archos.filemanager.R.layout.credentials_manager_fragment, container, false);
        mEmptyView = v.findViewById(com.archos.filemanager.R.id.empty_view);
        mEmptyTextView = (TextView)v.findViewById(com.archos.filemanager.R.id.empty_textview);
        mList = (RecyclerView)v.findViewById(com.archos.filemanager.R.id.recycler_view);
        mLayoutManager = new LinearLayoutManager(getActivity());
        mList.setLayoutManager(mLayoutManager);
        mList.setHasFixedSize(false);
        mList.setItemAnimator(new DefaultItemAnimator());
        mList.addItemDecoration(new DividerItemDecoration(getResources().getDrawable(com.archos.filemanager.R.drawable.listview_divider), DividerItemDecoration.VERTICAL_LIST));
        mCredentials = NetworkCredentialsDatabase.getInstance().getAllPersistentCredentials();
        mAdapter = new CredentialsManagerAdapter(mCredentials,getActivity(), this, this);
        mList.setAdapter(mAdapter);
        refreshCredentialsList();
        return v;
    }
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }



    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void refreshCredentialsList(){
        mCredentials = NetworkCredentialsDatabase.getInstance().getAllPersistentCredentials();
        if(mCredentials.size()>0) {
            mList.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            mAdapter.setCredentials(mCredentials);
            mAdapter.notifyDataSetChanged();
        }
        else{
            mEmptyView.setVisibility(View.VISIBLE);
            mList.setVisibility(View.GONE);

        }


    }

    @Override
    public void onItemClick(NetworkCredentialsDatabase.Credential credential) {
        CredentialsEditorDialog dialog = new CredentialsEditorDialog();
        Bundle args = new Bundle();
        args.putSerializable(CredentialsEditorDialog.CREDENTIAL,credential);
        dialog.setArguments(args);
        dialog.show(getActivity().getSupportFragmentManager(), CredentialsEditorDialog.class.getCanonicalName());
        dialog.setOnModifyListener(new CredentialsEditorDialog.OnModifyListener() {
            @Override
            public void onModify() {
                refreshCredentialsList();
            }
        });
    }

    @Override
    public boolean onItemLongClick(final NetworkCredentialsDatabase.Credential credential) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(credential.getUriString());
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(com.archos.filecorelibrary.R.string.samba_delete_settings);
        builder.setCancelable(false);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        NetworkCredentialsDatabase.getInstance().deleteCredential(credential.getUriString());
                        refreshCredentialsList();
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();

        return true;
    }
}
