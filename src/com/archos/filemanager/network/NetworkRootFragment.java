package com.archos.filemanager.network;

import java.util.List;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.samba.Share;
import com.archos.filemanager.BasePanelFragment;
import com.archos.filemanager.R;
import com.archos.filemanager.RootActivity;
import com.archos.filemanager.listing.ListingFragment;
import com.archos.filemanager.network.ServerCredentialsDialog.onConnectClickListener;
import com.archos.filemanager.network.shortcuts.ShortcutsFragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

public class NetworkRootFragment extends BasePanelFragment implements RootActivity.PanelModeListener {

    private static final String TAG = "NetworkRootFragment";

    private View mNetworkStateVeil;
    private ViewPager mViewPager;

    private SambaDiscoveryFragment mSambaDiscoveryFragment;
    private ShortcutsFragment mShortcutsFragment;
    private boolean mFragmentSetupDone = false;
    private Toast mToast;

    /** True if actually showing the network root, false when browsing */
    private boolean mShowingNetworkRoot = true;


    public NetworkRootFragment() {
        super();
        setRetainInstance(false);
    }

    /**
     * To handle the link between SambaDiscoveryFragment and ShortcutsFragment i need to have their instance, and it's pretty complicated
     * after a screen rotation when the ViewPager is re-instantiating the fragments all by itself.
     * This is the only way I found to do it, but it is ugly because it requires SambaDiscoveryFragment to call it, hence to know NetworkRootFragment...
     * @param SambaDiscoveryFragment
     */
    public void onSambaDiscoveryFragmentAttached(SambaDiscoveryFragment sdf) {
        mSambaDiscoveryFragment = sdf;
        childFragmentsLazyInit();
    }

    /**
     * To handle the link between ShortcutsFragment and SambaDiscoveryFragment i need to have their instance, and it's pretty complicated
     * after a screen rotation when the ViewPager is re-instantiating the fragments all by itself.
     * This is the only way I found to do it, but it is ugly because it requires ShortcutsFragment to call it, hence to know NetworkRootFragment...
     * @param ShortcutsFragment
     */
    public void onShortcutsFragmentAttached(ShortcutsFragment sf) {
        mShortcutsFragment = sf;
        childFragmentsLazyInit();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        setHasOptionsMenu(true);
        View v = inflater.inflate(R.layout.network_root_fragment, container, false);

        // Network state veil
        mNetworkStateVeil = v.findViewById(R.id.network_state_veil);
        updateNetworkStateUi(); // initialize veil

        // Setup for ViewPager case only
        mViewPager = (ViewPager)v.findViewById(R.id.viewpager);
        if (mViewPager!=null) {
            FragmentPagerAdapter adapter = new FragmentPagerAdapter(getChildFragmentManager()) {
                @Override
                public int getCount() {
                    return 2;
                }

                @Override
                public Fragment getItem(int position) {
                    Fragment f;
                    if (position==0) {
                        mSambaDiscoveryFragment = new SambaDiscoveryFragment();
                        f = mSambaDiscoveryFragment;
                        Log.d(TAG, "ViewPager getItem(0) mSambaDiscoveryFragment="+mSambaDiscoveryFragment);
                    }
                    else if (position==1) {
                        mShortcutsFragment = new ShortcutsFragment(); 
                        f = mShortcutsFragment;
                        Log.d(TAG, "ViewPager getItem(1) mShortcutsFragment="+mShortcutsFragment);
                    }
                    else {
                        throw new IllegalArgumentException("invalid position "+position);
                    }
                    return f;
                }


                @Override
                public CharSequence getPageTitle(int position) {
                    if (position==0) {
                        return getString(R.string.network_shared_folders);
                    }
                    else if (position==1) {
                        return getString(R.string.shortcuts);
                    }
                    throw new IllegalArgumentException("invalid position "+position);
                }
            };
            mViewPager.setAdapter(adapter);

            PagerTabStrip strip = (PagerTabStrip)mViewPager.findViewById(R.id.pager_tab_strip);
            strip.setTabIndicatorColor(getResources().getColor(android.R.color.white));
            strip.setDrawFullUnderline(true);
        }

        if (getActivity() instanceof RootActivity) {
            ((RootActivity) getActivity()).addPanelModeListener(this);
            onPanelModeChanged();
        }

        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mSambaDiscoveryFragment!=null && mShortcutsFragment!=null) {
            mSambaDiscoveryFragment.removeExternalDiscoveryListener(mShortcutsFragment);
        }

        if (getActivity() instanceof RootActivity) {
            ((RootActivity) getActivity()).removePanelModeListener(this);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        getActivity().registerReceiver(mNetworkStateListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    };

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(mNetworkStateListener);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        if(isActive()) { // do not do anything if not focused fragment
            menu.add(0, R.string.refresh_servers_list, 5, R.string.refresh_servers_list).setIcon(R.drawable.ic_menu_refresh).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, R.string.browse_ftp_server, 5, R.string.browse_ftp_server).setIcon(R.drawable.ic_menu_add).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if(isActive()) { // do not do anything if not focused fragment
            menu.findItem(R.string.refresh_servers_list).setVisible(mShowingNetworkRoot);
            menu.findItem(R.string.browse_ftp_server).setVisible(mShowingNetworkRoot);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        if(isActive()) { // do not do anything if not focused fragment
            if (item.getItemId() == R.string.refresh_servers_list) {
                mSambaDiscoveryFragment.forceStartDiscovery();
            } else if (item.getItemId() == R.string.browse_ftp_server) {
                ServerCredentialsDialog scd = new ServerCredentialsDialog();
                scd.setOnConnectClickListener(new onConnectClickListener() {
                    @Override
                    public void onConnectClick(Uri uri) {

                        ListingFragment f = new NetworkListingFragment();
                        // Root Uri argument is given with a bundle
                        Bundle args = new Bundle();
                        args.putParcelable(NetworkListingFragment.KEY_ROOT_URI, uri);
                        args.putString(NetworkListingFragment.KEY_ROOT_NAME, uri.getHost());
                        args.putParcelable(NetworkListingFragment.KEY_STARTING_URI, uri);
                        f.setArguments(args);
                        // CAUTION: the ListingFragment is added in this fragment, hence using the <b>ChildFragmentManager</b>
                        getChildFragmentManager().beginTransaction()
                                .setCustomAnimations(R.animator.fragment_in_from_right, 0, 0, R.animator.fragment_out_to_right)
                                .add(R.id.network_root_view, f, "ListingFragment")
                                .addToBackStack("fromRootToListing")
                                .commit();
                    }
                });
                scd.show(getFragmentManager(), scd.getClass().getName());
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPanelModeChanged() {
        boolean isLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        boolean isDoublePanel = (getActivity() instanceof RootActivity) && ((RootActivity) getActivity()).isDoublePanel();
        boolean isNotViewPager = (mViewPager == null);
        boolean isNotEnoughLarge = !getResources().getConfiguration().isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_XLARGE);
        boolean shouldBeHidden = isDoublePanel && isLandscape && isNotViewPager && isNotEnoughLarge;
        if (mShortcutsFragment != null && shouldBeHidden != mShortcutsFragment.isHidden()) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            if (mShortcutsFragment.isHidden()) {
                ft.show(mShortcutsFragment);
            } else {
                ft.hide(mShortcutsFragment);
            }
            ft.commit();
        }
    }

    /**
     * Need to forward the service instance to (all) the child BasePanelFragment contained in this fragment
     */
    @Override
    public void notifyServiceCreated(){
        List<Fragment> frags = getChildFragmentManager().getFragments();
        for(Fragment frag : frags){
            if(frag instanceof BasePanelFragment){
                ((BasePanelFragment)frag).notifyServiceCreated();
            }
        }
    }

    /**
     * Due to ViewPager-case and No-ViewPager-case, there are at least 3 different life-cycle scenario to handle for the creation
     * of SambaDiscoveryFragment and ShortcutsFragment...
     * Only viable solution I found at the end is to call this method at different places to setup the dependencies between
     * fragments only when all is ready
     */
    private void childFragmentsLazyInit() {
        if (!mFragmentSetupDone && (mSambaDiscoveryFragment!=null) && (mShortcutsFragment!=null)) {
            // The shortcuts fragment is only listener to the discovery
            mSambaDiscoveryFragment.addExternalDiscoveryListener(mShortcutsFragment);

            // We are handling clicks on the servers to start the navigation
            mSambaDiscoveryFragment.setOnShareOpenListener(new SambaDiscoveryFragment.OnShareOpenListener() {
                public void onShareOpen(Share share) {
                    final Uri uri = share.toUri();
                    ListingFragment f = new NetworkListingFragment();

                    mShowingNetworkRoot = false;

                    Bundle args = new Bundle();
                    args.putParcelable(NetworkListingFragment.KEY_STARTING_URI, uri);
                    args.putParcelable(NetworkListingFragment.KEY_ROOT_URI, uri);
                    args.putString(NetworkListingFragment.KEY_ROOT_NAME, share.getDisplayName());
                    f.setArguments(args);

                    // CAUTION: the ListingFragment is added in this fragment, hence using the <b>ChildFragmentManager</b>
                    getChildFragmentManager().beginTransaction()
                            .setCustomAnimations(R.animator.fragment_in_from_right, 0, 0, R.animator.fragment_out_to_right)
                            .add(R.id.network_root_view, f, "ListingFragment")
                            .addToBackStack("fromRootToListing")
                            .commit();
                }
            });

            // We are handling clicks on shortcuts
            mShortcutsFragment.setOnShortcutTapListener(new ShortcutsFragment.OnShortcutTapListener() {
                @Override
                public void onShortcutTap(Uri uri) {
                    // Build root Uri from shortcut Uri
                    String rootName = uri.getHost();
                    String rootUriString = uri.getScheme() + "://" + uri.getHost();
                    if (uri.getPort() != -1) {
                        rootUriString += ":" + uri.getPort();
                    }
                    rootUriString += "/"; // important to end with "/"
                    Uri rootUri = Uri.parse(rootUriString);
                    ListingFragment f = new NetworkListingFragment();

                    mShowingNetworkRoot = false;

                    Bundle args = new Bundle();
                    args.putParcelable(NetworkListingFragment.KEY_STARTING_URI, uri);
                    args.putParcelable(NetworkListingFragment.KEY_ROOT_URI, rootUri);
                    args.putString(NetworkListingFragment.KEY_ROOT_NAME, rootName);
                    f.setArguments(args);

                    // CAUTION: the ListingFragment is added in this fragment, hence using the <b>ChildFragmentManager</b>
                    getChildFragmentManager().beginTransaction()
                            .setCustomAnimations(R.animator.fragment_in_from_right, 0, 0, R.animator.fragment_out_to_right)
                            .add(R.id.network_root_view, f, "ListingFragment")
                            .addToBackStack("fromRootToListing")
                            .commit();
                }

                @Override
                public void onUnavailableShortcutTap(Uri uri) {
                    if (mToast != null) {
                        mToast.cancel(); // if we don't do that we have a very long toast in case user press on several shortcuts in row
                    }
                    mToast = Toast.makeText(getActivity(), getString(R.string.server_not_available_2, uri.getHost()), Toast.LENGTH_SHORT);
                    mToast.show();
                }
            });

            mFragmentSetupDone = true;
        }
    }

    @Override
    public boolean goBackOneLevel() {
        Log.d(TAG, "goBackOneLevel");

        // When the veil catches the touch events, it should also catch the "back" events
        if (mNetworkStateVeil.isClickable()) {
            // Returning true would make it behave like the user is stuck in this view (multiple BACK press doing nothing)
            // Returning false is not perfect (the application may finish) but it is the only viable option IMO
            return false;
        }

        // CAUTION: the ListingFragment was added in this fragment, hence using the <b>ChildFragmentManager</b>
        Fragment f = getChildFragmentManager().findFragmentById(R.id.network_root_view);
        Log.d(TAG, "goBackOneLevel f=" + f);
        if (f instanceof ListingFragment) {
            boolean consumed = ((ListingFragment)f).goBackOneLevel();
            if (!consumed) {
                // Go back to network root setup
                mShowingNetworkRoot = true;
                getChildFragmentManager().popBackStack();
            }
            return true;
        }
        else {
            return false;
        }
    }

    @Override
    public void setActive(boolean active, boolean updateUI) {
        // set active state to children
        List<Fragment> frags = getChildFragmentManager().getFragments();
        for(Fragment frag : frags) {
            if (frag instanceof BasePanelFragment) {
                ((BasePanelFragment)frag).setActive(active, false);
            }
        }
        super.setActive(active, updateUI);
    }

    @Override
    public boolean goBackToRoot() {
        Log.d(TAG, "goBackToRoot");
        // CAUTION: the ListingFragment was added in this fragment, hence using the <b>ChildFragmentManager</b>
        Fragment f = getChildFragmentManager().findFragmentById(R.id.network_root_view);
        Log.d(TAG, "goBackToRoot f="+f);
        if (f instanceof ListingFragment) {
            // Go back to network root setup
            mShowingNetworkRoot = true;
            getChildFragmentManager().popBackStack();
            return true;
        }
        else {
            // We are already on the network root view, do not consume the back
            return false;
        }
    }

    public void updateShortcutsList() {
        // in some use cases mShortcutsFragment is null, for example on screen rotation if a NetworkListingFragment has been added
        if (mShortcutsFragment!=null) {
            mShortcutsFragment.updateShortcutsList();
        }
    }

    /**
     * Will show a veil with a message when network is off.
     * This veil also catches the touch events
     */
    private void updateNetworkStateUi() {
        if (ArchosUtils.isNetworkConnected(getActivity())) {
            mNetworkStateVeil.animate().alpha(0);
            mNetworkStateVeil.setClickable(false);
        }
        else {
            mNetworkStateVeil.animate().alpha(0.9f);
            mNetworkStateVeil.setClickable(true);
        }
    }

    final private BroadcastReceiver mNetworkStateListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                updateNetworkStateUi();
                final boolean isLocalNetworkConnected = ArchosUtils.isLocalNetworkConnected(getActivity());
                Log.d(TAG, "mNetworkStateListener onReceive isLocalNetworkConnected="+isLocalNetworkConnected);
                if (isLocalNetworkConnected) {
                    // it is unlikely but mSambaDiscoveryFragment can be null here, because in ViewPager case
                    // the mSambaDiscoveryFragment is created after onStart and onResume (grrr...)
                    if (mSambaDiscoveryFragment!=null) {
                        mSambaDiscoveryFragment.startDiscovery();
                    }
                }
                else if (!isLocalNetworkConnected) {
                    if (mSambaDiscoveryFragment!=null) {
                        mSambaDiscoveryFragment.abortDiscovery();
                        mSambaDiscoveryFragment.showNoWifi();
                    }
                }
            }
        }
    };
}
