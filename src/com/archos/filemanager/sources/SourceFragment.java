
package com.archos.filemanager.sources;

import com.archos.environment.ArchosUtils;
import com.archos.environment.SystemPropertiesProxy;
import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.ExtStorageReceiver;
import com.archos.filemanager.listing.ListingFragment;
import com.archos.filemanager.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.storage.StorageManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public class SourceFragment extends Fragment implements View.OnClickListener, View.OnDragListener, SourceInterface {

    private static final String TAG = "SourceFragment";
    private static final boolean DBG = false;

    private static final boolean DRAG_FILES_OVER_SOURCES = false;

    private static final int sStorageSwapped = SystemPropertiesProxy.getInt("vold_swap_state",0);

    private static final int MSG_UPDATE_AVAILABLE_STORAGE = 1;
    private static final int MSG_START_DOUBLE_PANEL_ACTION = 2;

    private static final int AVAILABLE_STORAGE_UPDATE_PERIOD = 3000;    // in ms
    private static final int START_DOUBLE_PANEL_ACTION_DELAY = 1000;    // in ms

    private SourceInterface mSourceInterface;

    private LocalSourceButton mLocalSourceButton;
    private NetworkSourceButton mNetworkSourceButton;
    private List<SourceButton> mSourceButtons = new ArrayList<>();
    private List<SourceInfo> mOldSourceInfos = new ArrayList<>();
    private View mHoveredButton;
    private float mPreviousDragEventX;
    private float mPreviousDragEventY;

    private String mSelected;
    private ViewGroup mList;

    public void setNavigationInterface(SourceInterface navigationInterface) {
        mSourceInterface = navigationInterface;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSelected = null; // default
        if (savedInstanceState != null) {
            mSelected = savedInstanceState.getString("selected", mSelected);
        }
    }

    private void refresh(){
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        mList.removeAllViews();
        mOldSourceInfos.clear();
        //keep last sources
        for(SourceButton sb : mSourceButtons)
            mOldSourceInfos.add(sb.getSourceInfo());
        mSourceButtons.clear();
        final boolean hasExternal = storageManager.hasExtStorage();
        mLocalSourceButton = new LocalSourceButton(getActivity());
        mSourceButtons.add(mLocalSourceButton);
        if(mSelected==null)
            mSelected = mLocalSourceButton.getSourceInfo().mRootPath;
        mNetworkSourceButton = new NetworkSourceButton(getActivity());
        mNetworkSourceButton.updateValidity();
        mSourceButtons.add(mNetworkSourceButton);

        mList.addView(mLocalSourceButton);
        if (hasExternal) {

            if (hasExternal) {
                int i = 0;
                for (String s : storageManager.getExtSdcards()) {
                    SourceInfo sourceInfo = new SourceInfo(s, getString(R.string.sd_card_storage)+(i>0?" "+i:""));
                    SourceButton sb = new SourceButton(getActivity());
                    sb.setSourceInfo(sourceInfo);
                    sb.setDrawable(R.drawable.ic_sdcard_normal);
                    mSourceButtons.add(sb);
                    mList.addView(sb);
                    i++;
                }
                i=0;
                for (String s : storageManager.getExtUsbStorages()) {
                    SourceInfo sourceInfo = new SourceInfo(s, getString(R.string.usb_host_storage)+(i>0?" "+i:""));
                    SourceButton sb = new SourceButton(getActivity());
                    sb.setSourceInfo(sourceInfo);
                    sb.setDrawable(R.drawable.ic_usb_normal);;
                    mSourceButtons.add(sb);
                    mList.addView(sb);
                    i++;
                }
                i=0;
                for (String s : storageManager.getExtOtherStorages()) {
                    SourceInfo sourceInfo = new SourceInfo(s, getString(R.string.other_storage)+(i>0?" "+i:""));
                    SourceButton sb = new SourceButton(getActivity());
                    sb.setSourceInfo(sourceInfo);
                    sb.setDrawable(R.drawable.ic_usb_normal);;
                    mSourceButtons.add(sb);
                    mList.addView(sb);
                    i++;
                }
            }
        }
        mList.addView(mNetworkSourceButton);
        for (SourceButton sb : mSourceButtons) {
            sb.setOnClickListener(this);
            if (DRAG_FILES_OVER_SOURCES) {
                sb.setOnDragListener(this);
            }
        }

        for(SourceButton button : mSourceButtons){
            button.setSelected(button.getSourceInfo().mRootPath.equals(mSelected));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.main_list, container);



        mList = (ViewGroup) v.findViewById(R.id.main_file_list);



        refresh();
        return v;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save selected
        outState.putString("selected", mSelected);

    }

    @Override
    public void onStart() {
        super.onStart();

        IntentFilter filter1 = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter1.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getActivity().registerReceiver(mNetworkStateListener, filter1);

        IntentFilter filter2 = new IntentFilter(ExtStorageReceiver.ACTION_MEDIA_MOUNTED);
        filter2.addAction(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED);
        filter2.addAction(ExtStorageReceiver.ACTION_MEDIA_CHANGED);
        filter2.addDataScheme("file");
        filter2.addDataScheme(ExtStorageReceiver.ARCHOS_FILE_SCHEME);
        getActivity().registerReceiver(mExternalStorageReceiver, filter2);

        for (SourceButton sb : mSourceButtons) {
            sb.updateValidity();
            sb.updateAvailableStorage();
        }

        // Start the periodic update of the available storage display
        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_AVAILABLE_STORAGE, AVAILABLE_STORAGE_UPDATE_PERIOD);
    }

    @Override
    public void onStop() {
        super.onStop();

        getActivity().unregisterReceiver(mExternalStorageReceiver);
        getActivity().unregisterReceiver(mNetworkStateListener);

        // Stop the periodic update of the available storage display
        mHandler.removeMessages(MSG_UPDATE_AVAILABLE_STORAGE);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_AVAILABLE_STORAGE) {
                for (SourceButton sb : mSourceButtons) {
                    sb.updateAvailableStorage();
                }
                // Schedule next update
                sendEmptyMessageDelayed(MSG_UPDATE_AVAILABLE_STORAGE, AVAILABLE_STORAGE_UPDATE_PERIOD);

            } else if (msg.what == MSG_START_DOUBLE_PANEL_ACTION) {
                // Handle the panels => the goal is to get both panels visible with the destination panel
                // (the one in which we didn't start dragging) close to source panel.
                // Handle the contents of the active panel => make as if the user had clicked on this button
                if (mHoveredButton != null && mHoveredButton.isEnabled()) {
                    onSourceSelected(mHoveredButton, true);
                }
            }
        }
    };

    @Override
    public void onClick(View view) {
        onSourceSelected(view, false);
    }

    private void onSourceSelected(View button, boolean hovered) {

        if (button.equals(mNetworkSourceButton)) {
            mSourceInterface.onNetworkSelected(hovered);
        }

        else{
            mSourceInterface.onSourceButtonSelected(((SourceButton)button).getSourceInfo(),hovered);
        }

        // Select the clicked one and deselect all the other ones
        mSelected = ((SourceButton)button).getSourceInfo().mRootPath;
        for (SourceButton sb : mSourceButtons) {
            sb.setSelected(sb.getSourceInfo().mRootPath.equals(mSelected));
        }
    }

    @Override
    public boolean onDrag(View view, DragEvent event) {
        boolean dragEventHandled = false;

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_STARTED " + view.getClass());
                mSourceInterface.onDragStarted();

                mHoveredButton = null;
                dragEventHandled = true;
                break;

            case DragEvent.ACTION_DRAG_ENTERED:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_ENTERED " + view.getClass());
                mPreviousDragEventX = (int)event.getX();
                mPreviousDragEventY = (int)event.getY();
                break;

            case DragEvent.ACTION_DRAG_LOCATION:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_LOCATION " + view.getClass());
                final float x = event.getX();
                final float y = event.getY();

                // Check which button is currently below the finger (hovered button)
                final View hoveredButton = view;

                // Update the display if needed
                if (!hoveredButton.equals(mHoveredButton)) {
                    // Highlight the hovered item if it is valid and enabled
                    changeButtonBackground(hoveredButton, true);

                    // Remove the highlight on the previous hovered item if it was valid and enabled
                    changeButtonBackground(mHoveredButton, false);

                    mHoveredButton = hoveredButton;
                }

                // Some touch-screens keep sending ACTION_DRAG_LOCATION events repeatedly
                // even if the finger doesn't move => ignore these events if the drag point doesn't 
                // move (or only by a very small amount to filter the touchscreen erratic response)
                if (Math.abs(x - mPreviousDragEventX) > ListingFragment.VALID_MOVE_EVENT_THRESHOLD || Math.abs(y - mPreviousDragEventY) > ListingFragment.VALID_MOVE_EVENT_THRESHOLD) {
                    //Log.d(TAG, "onDrag : ACTION_DRAG_LOCATION at x=" + x + " y=" + y);

                    // Send a delayed request to open the double panel if the hovered item is valid
                    // but make sure to cancel any previous pending request so that the double panel
                    // mode is not enabled as long as the user keeps moving its finger
                    mHandler.removeMessages(MSG_START_DOUBLE_PANEL_ACTION);

                    // Allow an action if the hovered item is valid and enabled
                    if (hoveredButton != null && hoveredButton.isEnabled()) {
                        Message msg = mHandler.obtainMessage(MSG_START_DOUBLE_PANEL_ACTION);
                        mHandler.sendMessageDelayed(msg, START_DOUBLE_PANEL_ACTION_DELAY);
                    }
                    mPreviousDragEventX = x;
                    mPreviousDragEventY = y;
                }
                break;

            case DragEvent.ACTION_DRAG_EXITED:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_EXITED " + view.getClass());
                mHandler.removeMessages(MSG_START_DOUBLE_PANEL_ACTION);
                changeButtonBackground(mHoveredButton, false);
                mHoveredButton = null;
                break;

            case DragEvent.ACTION_DRAG_ENDED:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_ENDED " + view.getClass());
                mHandler.removeMessages(MSG_START_DOUBLE_PANEL_ACTION);
                changeButtonBackground(mHoveredButton, false);
                break;
        }

        return dragEventHandled;
	}

    private void changeButtonBackground(View button, boolean highlighted) {
        if (button != null && button.isEnabled()) {
            // need to save the padding and reapply it because changing the background resource seems to reset the padding...
            final int paddingLeft = button.getPaddingLeft();
            final int paddingRight = button.getPaddingRight();
            final int paddingTop = button.getPaddingTop();
            final int paddingBottom = button.getPaddingBottom();
            button.setBackgroundResource(highlighted ? R.drawable.main_list_item_background_hovered : R.drawable.main_list_item_background);
            button.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        }
    }

    private final BroadcastReceiver mExternalStorageReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            refresh();
            if(intent.getAction().equals(ExtStorageReceiver.ACTION_MEDIA_UNMOUNTED)){ //we need to check if current storage is still available
                SourceInfo removedSource = null;
                for(SourceInfo si : mOldSourceInfos){
                    boolean isStillThere = false;
                    for(SourceButton sb : mSourceButtons) {
                        if (sb.getSourceInfo().equals(si)) {
                            isStillThere = true;
                            break;
                        }
                    }
                    if(!isStillThere) {
                        removedSource = si;
                        break;
                    }
                }
                if(removedSource!=null){
                    mSourceInterface.onSourceUnmounted(removedSource, mLocalSourceButton.getSourceInfo());
                }
            }
        }
    };

    private BroadcastReceiver mNetworkStateListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                if(mNetworkSourceButton!=null)
                mNetworkSourceButton.updateValidity();
            }
        }
    };



    /**
     * implements SourceInterface.
     * Called when the network source is focused/selected in the activity
     */
    @Override
    public void onNetworkSelected(boolean hovered) {
        mSelected = mNetworkSourceButton.getSourceInfo().mRootPath;
        for (SourceButton sb : mSourceButtons) {
            sb.setSelected(sb == mNetworkSourceButton);
        }
    }

    @Override
    public void onSourceButtonSelected(SourceInfo info, boolean hovered) {
        mSelected = info.mRootPath;
        for (SourceButton sb : mSourceButtons) {
            sb.setSelected(sb.getSourceInfo()!=null&&sb.getSourceInfo().equals(info));
        }
    }


    /**
     * implements SourceInterface.
     */
    @Override
    public void onDragStarted() {}

    @Override
    public void onSourceUnmounted(SourceInfo unmountedSource, SourceInfo fallbackSource) {

    }

}
