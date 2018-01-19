package com.archos.filemanager.network;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.filecorelibrary.samba.Share;
import com.archos.filecorelibrary.samba.Workgroup;
import com.archos.filemanager.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class SambaDiscoveryFragment extends Fragment implements SambaDiscovery.Listener {

    private static final String TAG = "SambaDiscoveryFragment";

    private SambaDiscovery mSambaDiscovery;
    private RecyclerView mDiscoveryList;
    private RecyclerView.LayoutManager mLayoutManager;
    private WorkgroupAndServerAdapter mAdapter;
    private View mProgressSmallPlaceHolder;
    private View mProgressLargePlaceHolder;
    private ProgressBar mProgress;
    private ProgresStatus mProgressStatus;
    private View mEmptyView;
    private TextView mEmptyTextView;
    private View mRetryButton;

    private float mLargeToSmallTranslationX;
    private float mLargeToSmallTranslationY;
    private float mLargeToSmallScale = -1; // means it need to be initialized

    /**
     * Used to store an external listener that are given while the SambaDiscovery object does not exist yet.
     * This is needed because in some case we are called while we don't have the Context yet.
     * (FYI due to the ViewPager used in NetworkRootFragment)
     */
    private Set<SambaDiscovery.Listener> mDelayedExternalListeners = new HashSet<SambaDiscovery.Listener>();

    enum ProgresStatus {
        NONE, // no progress displayed
        LARGE, // large progress in the middle of the view
        ANIMATION, // transition between LARGE and SMALL
        SMALL // Small progress at the top of the view
    }

    public interface OnShareOpenListener {
        public void onShareOpen(Share share);
    }

    public SambaDiscoveryFragment() {
        Log.d(TAG, "SambaDiscoveryFragment() constructor "+this);
        setRetainInstance(false);
        // Adapter need to be instantiate ASAP because setOnShareOpenListener() may be called before onCreateView()
        mAdapter = new WorkgroupAndServerAdapter();
    }

    /**
     * If someone from outside this fragment need to be updated on the discovered shares
     * @param listener
     */
    public void addExternalDiscoveryListener(SambaDiscovery.Listener listener) {
        Log.d(TAG, "addExternalDiscoveryListener this=" + this);
        Log.d(TAG, "addExternalDiscoveryListener mSambaDiscovery=" + mSambaDiscovery);
        if (mSambaDiscovery!=null) {
            Log.d(TAG, "addExternalDiscoveryListener "+listener);
            mSambaDiscovery.addListener(listener);
        } else {
            Log.d(TAG, "addExternalDiscoveryListener delayed"+listener);
            mDelayedExternalListeners.add(listener);
        }
    }
    public void removeExternalDiscoveryListener(SambaDiscovery.Listener listener) {
        Log.d(TAG, "removeExternalDiscoveryListener " + listener);
        mSambaDiscovery.removeListener(listener);
    }

    public void setOnShareOpenListener(OnShareOpenListener listener) {
        mAdapter.setOnShareOpenListener(listener);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // NetworkRootFragment handles the link between SambaDiscoveryFragment and ShortcutsFragment.
        // This call is the only way I found to do it that works in all cases, but it is ugly because it requires SambaDiscoveryFragment to know NetworkRootFragment...
        if (getParentFragment() instanceof NetworkRootFragment) {
            ((NetworkRootFragment)getParentFragment()).onSambaDiscoveryFragmentAttached(this);
        }

        // Instantiate the SMB discovery as soon as we get the activity context
        mSambaDiscovery = new SambaDiscovery(activity);
        mSambaDiscovery.setMinimumUpdatePeriodInMs(100);

        Log.d(TAG, "onAttach this="+this);
        Log.d(TAG, "onAttach mSambaDiscovery=" + mSambaDiscovery);

        // Add external listeners if there are any
        for (SambaDiscovery.Listener l : mDelayedExternalListeners) {
            Log.d(TAG, "onAttach addListener "+l);
            mSambaDiscovery.addListener(l);
        }
        mDelayedExternalListeners.clear();

    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(TAG, "onDetach");
        mSambaDiscovery.abort();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        mSambaDiscovery.addListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSambaDiscovery.removeListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable("mLayoutManager", mLayoutManager.onSaveInstanceState()); // Save the layout manager state (that's cool we don't even know what it is doing inside!)
        mAdapter.onSaveInstanceState(outState);        // Save the adapter "saved instance" parameters

        // Save state of the empty view
        outState.putInt("mEmptyView.getVisibility()", mEmptyView.getVisibility());
        outState.putInt("mEmptyTextView.getTextColor()", mEmptyTextView.getCurrentTextColor());
        outState.putCharSequence("mEmptyTextView.getText()", mEmptyTextView.getText());
        outState.putInt("mRetryButton.getVisibility()", mRetryButton.getVisibility());

        // Remember if the discovery is still running in order to restart it when restoring the fragment
        outState.putBoolean("isRunning", mSambaDiscovery.isRunning());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View v = inflater.inflate(R.layout.samba_discovery_fragment, container, false);

        mDiscoveryList = (RecyclerView)v.findViewById(R.id.discovery_list);
        mProgressSmallPlaceHolder = v.findViewById(R.id.progress_small_placeholder);
        mProgressLargePlaceHolder = v.findViewById(R.id.progress_large_placeholder);
        mProgress = (ProgressBar)v.findViewById(R.id.progress_large);
        mEmptyView = v.findViewById(R.id.empty_view);
        mEmptyTextView = (TextView)v.findViewById(R.id.empty_textview);
        mRetryButton = v.findViewById(R.id.retry_button);
        mRetryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDiscovery();
            }
        });

        setProgressState(ProgresStatus.NONE);

        View titleView = v.findViewById(R.id.title);
        titleView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                    mLargeToSmallScale = -1;
                }
            }
        });

        v.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                if (mLargeToSmallScale == -1) {
                    Log.d(TAG, "Compute the translation and scale to transform from the large progress to the small progress");
                    // Compute the translation and scale to transform from the large progress to the small progress
                    mLargeToSmallScale = mProgressSmallPlaceHolder.getWidth() / (float) mProgressLargePlaceHolder.getWidth();
                    int smallLocation[] = new int[2];
                    int largeLocation[] = new int[2];
                    mProgressSmallPlaceHolder.getLocationOnScreen(smallLocation);
                    mProgressLargePlaceHolder.getLocationOnScreen(largeLocation);
                    float smallCenterX = smallLocation[0] + mProgressSmallPlaceHolder.getWidth() / 2f;
                    float smallCenterY = smallLocation[1] + mProgressSmallPlaceHolder.getHeight() / 2f;
                    float largeCenterX = largeLocation[0] + mProgressLargePlaceHolder.getWidth() / 2f;
                    float largeCenterY = largeLocation[1] + mProgressLargePlaceHolder.getHeight() / 2f;
                    Log.d(TAG, "*** " + smallCenterX + " " + smallCenterY + " | " + largeCenterX + " " + largeCenterY);
                    mLargeToSmallTranslationX = smallCenterX - largeCenterX;
                    mLargeToSmallTranslationY = smallCenterY - largeCenterY;
                }
            }
        });

        mLayoutManager = new LinearLayoutManager(getActivity());
        mDiscoveryList.setLayoutManager(mLayoutManager);
        mDiscoveryList.setHasFixedSize(false); // there are separators
        mDiscoveryList.setAdapter(mAdapter);

        mEmptyView.setVisibility(View.GONE);

        boolean isLocalNetworkConnected = ArchosUtils.isLocalNetworkConnected(getActivity());

        if (savedInstanceState != null) {
            mAdapter.onRestoreInstanceState(savedInstanceState); // Restore the adapter "saved instance" parameters
            mLayoutManager.onRestoreInstanceState(savedInstanceState.getParcelable("mLayoutManager")); // Restore the layout manager state
            // Restore the empty view state
            // noinspection ResourceType
            mEmptyView.setVisibility(savedInstanceState.getInt("mEmptyView.getVisibility()"));
            mEmptyTextView.setTextColor(savedInstanceState.getInt("mEmptyTextView.getTextColor()"));
            mEmptyTextView.setText(savedInstanceState.getCharSequence("mEmptyTextView.getText()"));
            // noinspection ResourceType
            mRetryButton.setVisibility(savedInstanceState.getInt("mRetryButton.getVisibility()"));
            // Restart the discovery if it was running when saving the instance
            if (savedInstanceState.getBoolean("isRunning")) {
                startDiscovery();
            }
        }
        else {
            // First initialization, start the discovery (if there is connectivity)
            if (isLocalNetworkConnected) {
                startDiscovery();
            }
        }
        if(!isLocalNetworkConnected) {
            showNoWifi();
        }

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mSambaDiscovery.abort();
    }

    /**
     * Start the discovery.
     * Not needed at initialization since the fragment will start it by itself (if there is connectivity)
     * The discovery is not restarted if it is already running (i.e. not a problem if this method is called too often)
     */
    public void startDiscovery() {
        Log.d(TAG, "startDiscovery");
        if (!mSambaDiscovery.isRunning()) { // avoid restarting due to complex fragment + CONNECTIVITY_ACTION BroadcastReceiver init
            mSambaDiscovery.start();
        }
    }

    /**
     * Start or restart the discovery.
     * If discovery is already running it is aborted and restarted
     */
    public void forceStartDiscovery() {
        Log.d(TAG, "forceStartDiscovery");
        mSambaDiscovery.start();
    }

    public void abortDiscovery() {
        Log.d(TAG, "abortDiscovery");
        mSambaDiscovery.abort();
        setProgressState(ProgresStatus.NONE);
    }

    public void showNoWifi() {
        Log.d(TAG, "showNoWifi");
        mAdapter.clearData();
        int textColor = getResources().getColor(R.color.empty_view_text_color);
        showEmptyView(getString(R.string.nonetwork_message), textColor, false);
    }

    // SambaDiscovery.Listener implementation
    @Override
    public void onDiscoveryStart() {
        if (mAdapter.getItemCount() == 0) {
            setProgressState(ProgresStatus.LARGE);
            mEmptyView.setVisibility(View.GONE);
        } else {
            // When restarting the discovery it does not look nice to have the large2small animation again
            setProgressState(ProgresStatus.SMALL);
        }
    }

    // SambaDiscovery.Listener implementation
    @Override
    public void onDiscoveryEnd() {
        setProgressState(ProgresStatus.NONE);
        if (mSambaDiscovery.getmWorkgroups().size() == 0) {
            int textColor = getResources().getColor(R.color.empty_view_text_color);
            showEmptyView(getString(R.string.no_server), textColor, true);
            mAdapter.clearData();
        }
    }

    // SambaDiscovery.Listener implementation
    @Override
    public void onDiscoveryUpdate(List<Workgroup> workgroups) {
        // animate large2small when some shares have been discovered
        if (workgroups.size() > 0) {
            setProgressState(ProgresStatus.ANIMATION);
        }

        mAdapter.updateData(workgroups);
    }

    // SambaDiscovery.Listener implementation
    @Override
    public void onDiscoveryFatalError() {
        Log.d(TAG, "onDiscoveryFatalError");
        setProgressState(ProgresStatus.NONE);
        int textColor = getResources().getColor(R.color.error_view_text_color);
        showEmptyView(getString(R.string.error_listing),  textColor, false);
        mAdapter.clearData();
    }

    private void showEmptyView(String message, int textColor, boolean showRetryButton) {
        if (mEmptyView.getVisibility() == View.GONE) {
            mEmptyView.setAlpha(0);
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyTextView.setText(message);
            mEmptyTextView.setTextColor(textColor);
            mRetryButton.setVisibility(showRetryButton ? View.VISIBLE : View.GONE);
            // Quick fade-in
            mEmptyView.animate().alpha(1).setDuration(100);
        }
    }

    private void setProgressState(ProgresStatus state) {
        switch (state) {
            case NONE:
                if (mProgressStatus == ProgresStatus.ANIMATION) {
                    mProgress.animate().cancel();
                }
                mProgress.setVisibility(View.INVISIBLE);
                break;
            case LARGE:
                if (mProgressStatus == ProgresStatus.ANIMATION) {
                    mProgress.animate().cancel();
                }
                setProgressLarge();
                mProgress.setVisibility(View.VISIBLE);
                break;
            case ANIMATION:
                // Start animation if it is not the case yet. Nothing to do if animation is already on-going.
                // Do not animate if it is small already.
                if (mProgressStatus != ProgresStatus.ANIMATION && mProgressStatus != ProgresStatus.SMALL) {
                    setProgressSmall(true);
                }
                mProgress.setVisibility(View.VISIBLE);
                break;
            case SMALL:
                if (mProgressStatus == ProgresStatus.ANIMATION) {
                    mProgress.animate().cancel();
                }
                setProgressSmall(false);
                mProgress.setVisibility(View.VISIBLE);
                break;
        }
        mProgressStatus = state;
    }

    private void setProgressLarge() {
        mProgress.setTranslationX(0);
        mProgress.setTranslationY(0);
        mProgress.setScaleX(1);
        mProgress.setScaleY(1);
    }

    private void setProgressSmall(boolean animated) {
        if (animated) {
            setProgressLarge(); // safer to reset position and scale first
            mProgress.animate()
            .translationX(mLargeToSmallTranslationX)
            .translationY(mLargeToSmallTranslationY)
            .scaleX(mLargeToSmallScale)
            .scaleY(mLargeToSmallScale)
            .setInterpolator(new LinearInterpolator())
            .start();
        }
        else {
            mProgress.setTranslationX(mLargeToSmallTranslationX);
            mProgress.setTranslationY(mLargeToSmallTranslationY);
            mProgress.setScaleX(mLargeToSmallScale);
            mProgress.setScaleY(mLargeToSmallScale);
        }
    }

}
