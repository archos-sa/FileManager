package com.archos.filemanager.network;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.archos.filemanager.R;

public class ServerCredentialsDialog extends DialogFragment implements DialogInterface.OnShowListener {

    private AlertDialog mDialog;
    private SharedPreferences mPreferences;
    private String mUsername="";
    private String mPassword="";
    private int mPort=-1;
    private int mType=-1;
    private String mRemote="";
    private onConnectClickListener mOnConnectClick;
    final private static String FTP_LATEST_TYPE = "FTP_LATEST_TYPE";
    final private static String FTP_LATEST_ADDRESS = "FTP_LATEST_ADDRESS";
    final private static String FTP_LATEST_PORT = "FTP_LATEST_PORT";
    final private static String FTP_LATEST_USERNAME = "FTP_LATEST_USERNAME";

    final public static String USERNAME = "username";
    final public static String REMOTE = "remote_address";
    final public static String PORT = "port";
    final public static String PASSWORD = "password";
    final public static String TYPE = "type";
    final public static String PATH = "path";

    final public static int TYPE_FTP  = 0;
    final public static int TYPE_SFTP = 1;
    final public static int TYPE_FTPS = 2;
    final public static int TYPE_SMB  = 3;

    public static final String FOCUS_ON = "FOCUS_ON";
    public static final int FOCUS_REMOTE_ADDRESS = 0;
    public static final int FOCUS_USERNAME = 1;

    private onDismissListener mOnDismissListener;
    private String mPath;
    private EditText portEt;
    private EditText addressEt;
    private int mFocusOn;
    private EditText mFocusED;

    @Override
    public void onShow(DialogInterface dialogInterface) {
        mFocusED.requestFocus();
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mFocusED, InputMethodManager.SHOW_IMPLICIT);
    }

    public interface onConnectClickListener {
        public void onConnectClick(Uri uri);
    }

    public interface onDismissListener {
        public void dismiss();
    }

    public ServerCredentialsDialog() {}

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args  = getArguments();
        if(args != null){
            mUsername = args.getString(USERNAME,"");
            mPassword = args.getString(PASSWORD,"");
            mPort = args.getInt(PORT, -1);
            mType = args.getInt(TYPE, -1);
            mPath = args.getString(PATH, "");
            mRemote = args.getString(REMOTE,"");
            mFocusOn = args.getInt(FOCUS_ON,FOCUS_REMOTE_ADDRESS);
        }
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        // Get latest values from preference
        if (mUsername.isEmpty() && mPassword.isEmpty() && mPort == -1 && mType == -1 && mRemote.isEmpty()) {
            mRemote = mPreferences.getString(FTP_LATEST_ADDRESS, "");
            mUsername = mPreferences.getString(FTP_LATEST_USERNAME, "");
            mType = mPreferences.getInt(FTP_LATEST_TYPE, 0);
            mPort = mPreferences.getInt(FTP_LATEST_PORT, -1);
        }
        if (mPassword.isEmpty() && !mRemote.isEmpty()) {
            NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
            String uriToBuild = "";
            switch(mType) {
                case TYPE_FTP:  uriToBuild = "ftp";  break;
                case TYPE_SFTP: uriToBuild = "sftp"; break;
                case TYPE_FTPS: uriToBuild = "ftps"; break;
                case TYPE_SMB:  uriToBuild = "smb";  break;
                default:
                    throw new IllegalArgumentException("Invalid FTP type "+mType);
            }
            uriToBuild +="://"+mRemote+":"+mPort+"/";
            Credential cred = database.getCredential(uriToBuild);
            if(cred!=null){
                mPassword= cred.getPassword();
            }
        }
        final View v = getActivity().getLayoutInflater().inflate(R.layout.ssh_credential_layout, null);
        final Spinner typeSp = (Spinner)v.findViewById(R.id.ssh_spinner);
        addressEt = (EditText)v.findViewById(R.id.remote);
        portEt = (EditText)v.findViewById(R.id.port);
        final EditText usernameEt = (EditText)v.findViewById(R.id.username);
        final EditText passwordEt = (EditText)v.findViewById(R.id.password);
        final EditText pathEt = (EditText)v.findViewById(R.id.path);
        final CheckBox savePassword = (CheckBox)v.findViewById(R.id.save_password);

        int type = mType;
        typeSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == TYPE_SMB) {
                   // addressEt.setVisibility(View.INVISIBLE);
                    portEt.setVisibility(View.GONE);
                    pathEt.setVisibility(View.GONE);
                }
                else {
                    addressEt.setVisibility(View.VISIBLE);
                    pathEt.setVisibility(View.VISIBLE);
                    portEt.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        if (type == TYPE_FTP || type == TYPE_SFTP || type == TYPE_FTPS || type == TYPE_SMB) { // better safe than sorry
            typeSp.setSelection(type);
        }
        addressEt.setText(mRemote);
        pathEt.setText(mPath);
        int portInt =  mPort;
        String portString = (portInt != -1) ? Integer.toString(portInt) : "";
        portEt.setText(portString);
        usernameEt.setText(mUsername);
        passwordEt.setText(mPassword);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
        .setTitle(R.string.browse_ftp_server)
        .setView(v)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok,new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog,int id) {
                if (!addressEt.getText().toString().isEmpty()) {
                    final int type = typeSp.getSelectedItemPosition();
                    final String address = addressEt.getText().toString();
                    String path = pathEt.getText().toString();
                    int port = -1;
                    try {
                        port = Integer.parseInt(portEt.getText().toString());
                    } catch(NumberFormatException e) {}

                    // get default port if it's wrong
                    switch(type){
                        case TYPE_FTP:  if (port == -1)  port = 21; break;
                        case TYPE_SFTP: if (port == -1)  port = 22; break;
                        case TYPE_FTPS: if (port == -1)  port = 21; break;
                        case TYPE_SMB:  port = -1;                  break;
                        default:
                            throw new IllegalArgumentException("Invalid FTP type "+type);
                    }

                    final String username = usernameEt.getText().toString();
                    final String password = passwordEt.getText().toString();

                    // Store new values to preferences
                    mPreferences.edit()
                    .putInt(FTP_LATEST_TYPE, type)
                    .putString(FTP_LATEST_ADDRESS, address)
                    .putInt(FTP_LATEST_PORT, port)
                    .putString(FTP_LATEST_USERNAME, username)
                    .apply();
                    /*
                    SambaSingleSetting sss = new SambaSingleSetting(address+":"+port);
                    switch(type){
                        case 0: sss.setType(SambaSingleSetting.TYPE_FTP); break;
                        case 1: sss.setType(SambaSingleSetting.TYPE_SFTP); break;
                        default:
                            throw new IllegalArgumentException("Invalid FTP type "+type);
                    }
                    sss.setUsername(username);
                    sss.setPassword(password);
                    SambaConfiguration.setSingleSetting(sss);*/
                    String uriToBuild = "";
                    switch(type) {
                        case TYPE_FTP:  uriToBuild = "ftp";  break;
                        case TYPE_SFTP: uriToBuild = "sftp"; break;
                        case TYPE_FTPS: uriToBuild = "ftps"; break;
                        case TYPE_SMB:  uriToBuild = "smb";  break;
                        default:
                            throw new IllegalArgumentException("Invalid FTP type "+type);
                    }
                    //path needs to start by a "/"
                    if (path.isEmpty() || !path.startsWith("/")) {
                        path = "/" + path;
                    }
                    uriToBuild += "://" + (!address.isEmpty() ? address + (port != -1 ? ":" + port : "" ) : "" ) + path;
                    if (savePassword.isChecked()) {
                        NetworkCredentialsDatabase.getInstance().saveCredential(new Credential(username, password, uriToBuild, true));
                    } else {
                        NetworkCredentialsDatabase.getInstance().addCredential(new Credential(username, password, uriToBuild, true));
                    }
                    if (mOnConnectClick != null) {
                        mOnConnectClick.onConnectClick(Uri.parse(uriToBuild));
                    }

                }
                else
                    Toast.makeText(getActivity(), getString(R.string.ssh_remote_address_error), Toast.LENGTH_SHORT).show();
            }});
        mDialog = builder.create();
        mDialog.setCanceledOnTouchOutside(false);

        mFocusED = null;
        switch (mFocusOn){
            case FOCUS_REMOTE_ADDRESS:
                mFocusED = addressEt;
                break;
            case FOCUS_USERNAME:
                mFocusED = usernameEt;
                break;
        }

        mFocusED.setSelection(mFocusED.length());
        mDialog.setOnShowListener(this); //needed to make keyboard appear
        return mDialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        if (mOnDismissListener != null) {
            mOnDismissListener.dismiss();
        }
    }

    public void setOnConnectClickListener(onConnectClickListener onConnectClick) {
        mOnConnectClick = onConnectClick;
    }

    public void setOnDismissListener(onDismissListener listener) {
        mOnDismissListener = listener;
    }

}
