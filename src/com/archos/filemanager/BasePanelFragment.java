package com.archos.filemanager;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;

import com.archos.filemanager.sources.SourceInfo;

public abstract class BasePanelFragment extends Fragment {

    private static final String TAG = "BasePanelFragment";
    private static final boolean DBG = true;

    private NavigationInterface mNavigationInterface;
    private boolean mActive = true;
    private boolean mUpdateUI = true;
    private View mVeil;
    private float mScaleWhenInactive;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mNavigationInterface = (NavigationInterface)getActivity();
    }

    @Override
    public void onDetach() {
        mNavigationInterface = null; // bsts, avoid activity leak
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // restore active state
        if (savedInstanceState != null) {
            mActive = savedInstanceState.getBoolean("mActive", mActive);
            mUpdateUI = savedInstanceState.getBoolean("mUpdateUI", mUpdateUI);
        }
        TypedValue floatValue = new TypedValue();
        getResources().getValue(R.dimen.back_panel_scale, floatValue, true);
        mScaleWhenInactive = floatValue.getFloat();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVeil = view.findViewById(R.id.veil);
        mVeil.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mNavigationInterface.focusRequest(BasePanelFragment.this);
            }
        });
        mVeil.setClickable(false);

        if (mUpdateUI) {
            // initialize active state
            updateUiActiveState(mActive, false);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean("mActive", mActive);
        outState.putBoolean("mUpdateUI", mUpdateUI);
    }

    /**
     * Used when there are two panels, to show which one is "active" (or "selected", or "focused", not sure what the correct word is...)
     * when updateUI is to false, ui won't change, useful for a basepanelfragment is inside another basepanelfragment.
     * @param active
     * @param updateUI
     */
    public void setActive(boolean active, boolean updateUI) {
        if (DBG) Log.d(TAG, "setActive " + active);

        mActive = active;
        mUpdateUI = updateUI;
        if (mUpdateUI) {
            updateUiActiveState(mActive, true);
        }
    }

    public boolean isActive() {
        return mActive;
    }

    public void notifyServiceCreated() {
    }

    private void updateUiActiveState(boolean active, boolean animated) {
        if (getView() != null) {
            final float scale = active ? 1f : mScaleWhenInactive;
            if (animated) {
                getView().animate().scaleX(scale).scaleY(scale);
            } else {
                getView().setScaleX(scale);
                getView().setScaleX(scale);
            }
        }
        if (mVeil != null) {
            // catch the click only in inactive state
            mVeil.setClickable(!active);
            if (animated) {
                mVeil.animate().alpha(active ? 0f : 1f);
            } else {
                mVeil.setAlpha(active ? 0f : 1f);
            }
        }
    }

    /**
     * @return true if the back is consumed
     */
    public abstract boolean goBackOneLevel();

    /**
     * @return true if the back is consumed
     */
    public abstract boolean goBackToRoot();


}
