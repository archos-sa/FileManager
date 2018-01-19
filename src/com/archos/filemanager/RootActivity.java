package com.archos.filemanager;

import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.util.Log;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.Utils;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filemanager.FileCopyFragment.Listener;
import com.archos.filemanager.FileManagerService.ServiceListener;
import com.archos.filemanager.listing.FileLauncher;
import com.archos.filemanager.listing.LocalListingFragment;
import com.archos.filemanager.network.CredentialsManagerPreferenceActivity;
import com.archos.filemanager.network.NetworkRootFragment;
import com.archos.filemanager.sources.SourceFragment;
import com.archos.filemanager.sources.SourceInfo;
import com.archos.filemanager.sources.SourceInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vapillon
 *
 */
public class RootActivity extends FragmentActivity implements SourceInterface, NavigationInterface, ServiceListener, OnGlobalLayoutListener {

    private static final String TAG = "RootActivity";
    private static final boolean DBG = true;

    private static final int COPY_INFO_LAUNCH_DELAY_MS = 500;
    public static final String EXTRA_FOLDER_TO_OPEN = "folder_to_open";
    public static final String EXTRA_MIMETYPE = "mimetype";
    public static final String EXTRA_EXTENSION = "extension";

    private LinearLayout mPanelsContainer;
    private ViewGroup mPanelOneContainer;
    private ViewGroup mPanelTwoContainer;
    private FrameLayout mInfoContainer;
    private View mInitialDragView;

    private BasePanelFragment mFocusedPanelFragment = null;
    private BasePanelFragment mDefocusedPanelFragment = null;
    private FileCopyFragment mFileCopyFragment = null;

    private Listener mFileCopyFragmentListener = new Listener() {

        @Override
        public void onMinimizePressed() {
            hideCopyFragment(true);
        }

        @Override
        public void onCancelPressed() {
            hideCopyFragment(false);
        }
    };

    /**
     * The drawer sliding from the left. Is null when on tablets
     */
    private DrawerLayout mDrawer;
    private ActionBarDrawerToggle mDrawerToggle;

    /**
     * The fragment containing the main access points to Internal, SDcard, network, USB
     */
    private SourceFragment mSourceFragment;

    /**
     * True if the two panels are currently displayed
     */
    private boolean mIsDoublePanel = false;

    private ArrayList<PanelModeListener> mPanelModeListeners;
    private PermissionChecker mPermissionChecker;

    public interface PanelModeListener {
        void onPanelModeChanged();
    }

    public void addPanelModeListener(PanelModeListener listener) {
        if (!mPanelModeListeners.contains(listener)) {
            mPanelModeListeners.add(listener);
        }
    }

    public void removePanelModeListener(PanelModeListener listener) {
        mPanelModeListeners.remove(listener);
    }

    private void notifyPanelModeListeners() {
        for (PanelModeListener listener : mPanelModeListeners){
            listener.onPanelModeChanged();
        }
    }

    public boolean isDoublePanel() {
        return mIsDoublePanel;
    }

    /**
     * True if user opened the drawer by himself/herself at least one
     */
    private boolean mUserLearnedDrawer;
    final private String PREF_USER_LEARNED_DRAWER = "pref_user_learned_drawer";

    private ProgressDrawable mProgressDrawable;


    /**
     * Service to handle actions on file (copy, cut, delete, etc)
     */
    private ServiceConnection mFileManagerServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            onFileManagerServiceCreated();
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    private void onFileManagerServiceCreated() {
        FileManagerService.fileManagerService.addListener(RootActivity.this);
        mProgressDrawable.notifyServiceCreated();
        List<Fragment> frags = getSupportFragmentManager().getFragments();
        for (Fragment frag : frags) {
            if (frag instanceof BasePanelFragment) {
                ((BasePanelFragment)frag).notifyServiceCreated();
            }
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DBG) Log.d(TAG, "onCreate " + (savedInstanceState!=null));
        mPermissionChecker = new PermissionChecker();
        // Setup the window
        setContentView(R.layout.root_activity);
        mPanelsContainer = (LinearLayout)findViewById(R.id.panels_container);
        mInfoContainer = (FrameLayout)findViewById(R.id.info_container);
        mPanelOneContainer = (ViewGroup)mPanelsContainer.findViewById(R.id.f1);
        mPanelTwoContainer = (ViewGroup)mPanelsContainer.findViewById(R.id.f2);
        mSourceFragment = (SourceFragment)getSupportFragmentManager().findFragmentById(R.id.source_drawer);
        mProgressDrawable = (ProgressDrawable)findViewById(R.id.progress_drawable);
        mSourceFragment.setNavigationInterface(this);

        // ActionBar setup depending if we are in SlidingMenu case or not
        ActionBar actionBar = getActionBar();
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if(mDrawer!=null){
            drawerLayoutSetup(savedInstanceState);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowHomeEnabled(false);
        }
        else {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM, ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        }

        int orientation = getResources().getConfiguration().orientation;
        mPanelsContainer.setOrientation(orientation==Configuration.ORIENTATION_LANDSCAPE ? LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);

        //copy fragment
        if(mProgressDrawable!=null)
            mProgressDrawable.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    displayCopyFragment(true);
                }
            });

        //load service
        if(FileManagerService.fileManagerService==null) {
            Intent connectionIntent = new Intent(this, FileManagerService.class);
            bindService(connectionIntent, mFileManagerServiceConnection, Context.BIND_AUTO_CREATE);
        }
        else {
            onFileManagerServiceCreated();
        }

        //load credentials
        NetworkCredentialsDatabase.getInstance().loadCredentials(this);

        // Load local content in container 1 and hide container 2
        mPanelOneContainer.setVisibility(View.VISIBLE);
        mPanelTwoContainer.setVisibility(View.GONE);
        mPanelOneContainer.setOnDragListener(mPanelFocusDragListener);
        mPanelTwoContainer.setOnDragListener(mPanelFocusDragListener);

        // Setup the layout transition once the layout is actually done to have access to the width and/or height of the panel container view
        mPanelsContainer.getViewTreeObserver().addOnGlobalLayoutListener(this);

        if (savedInstanceState == null) {
            // initialize first fragment (if we are creating a brand new activity)
            LocalListingFragment local = new LocalListingFragment();
            local.setSourceInfo(new SourceInfo("file://"
                    + Environment.getExternalStorageDirectory().getPath(), getString(R.string.internal_storage)));
            loadFragmentInCurrentPanel(local);
        }
        else {
            // This must be done here instead of in onRestoreInstanceState() because onPrepareOptionsMenu() is called before onRestoreInstanceState()!
            mIsDoublePanel = savedInstanceState.getBoolean("mIsDoublePanel", mIsDoublePanel);
        }
        mPanelModeListeners = new ArrayList<>();
        onNewIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("drawerOpen", isDrawerOpen());
        outState.putBoolean("mIsDoublePanel", mIsDoublePanel);
        outState.putInt("mPanelOneContainer.getVisibility()", mPanelOneContainer.getVisibility());
        outState.putInt("mPanelTwoContainer.getVisibility()", mPanelTwoContainer.getVisibility());
        outState.putInt("mInfoContainer.getVisibility()", mInfoContainer.getVisibility());
        outState.putBoolean("FocusOnF1", mPanelOneContainer.equals(getPanelFragmentContainer(mFocusedPanelFragment)));
    }

    @Override
    public void onPause(){
        super.onPause();
        if(FileManagerService.fileManagerService!=null)
            FileManagerService.fileManagerService.deleteObserver(this);
    }
    @Override
    public void onNewIntent(Intent intent){ //called again in "onCreate"
        super.onNewIntent(intent);
        if(intent.hasExtra(EXTRA_MIMETYPE)&&!"application/zip".equals(intent.getStringExtra(EXTRA_MIMETYPE))){
            FileLauncher.openFile(Uri.parse(intent.getStringExtra(EXTRA_FOLDER_TO_OPEN)), intent.getStringExtra(EXTRA_EXTENSION), intent.getStringExtra(EXTRA_MIMETYPE), this);
        }
        else if(intent.hasExtra(EXTRA_FOLDER_TO_OPEN)){
            SourceInfo info = new SourceInfo(intent.getStringExtra(EXTRA_FOLDER_TO_OPEN), Utils.getName(Uri.parse(intent.getStringExtra(EXTRA_FOLDER_TO_OPEN))));
            onSourceButtonSelected(info, false);
        }
    }
    @Override
    public void onResume(){
        super.onResume();

        if(FileManagerService.fileManagerService!=null){
            FileManagerService.fileManagerService.addListener(this);
        }
        mPermissionChecker.checkAndRequestPermission(this);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        mPermissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState");
        // Restore the UI (the fragments are restored automatically by the framework)
        mPanelOneContainer.setVisibility(savedInstanceState.getInt("mPanelOneContainer.getVisibility()"));
        mPanelTwoContainer.setVisibility(savedInstanceState.getInt("mPanelTwoContainer.getVisibility()"));
        mInfoContainer.setVisibility(savedInstanceState.getInt("mInfoContainer.getVisibility()"));

        // Get the fragment that have been recreated by the framework
        BasePanelFragment f1 = (BasePanelFragment)getSupportFragmentManager().findFragmentById(R.id.f1);
        BasePanelFragment f2 = (BasePanelFragment)getSupportFragmentManager().findFragmentById(R.id.f2);

        // Restore the focus
        if (savedInstanceState.getBoolean("FocusOnF1")) {
            mFocusedPanelFragment = f1;
            mDefocusedPanelFragment = f2;
        } else {
            mFocusedPanelFragment = f2;
            mDefocusedPanelFragment = f1;
        }

        // Check if there is a copy fragment
        mFileCopyFragment = (FileCopyFragment) getSupportFragmentManager().findFragmentById(R.id.info_container); //restore context fragment
        if(mFileCopyFragment!=null) {
            mFileCopyFragment.setListener(mFileCopyFragmentListener);
        }
        else {
            // Check if a copy is in progress
            if(FileManagerService.fileManagerService!=null && FileManagerService.fileManagerService.isActionRunning()) {
                // Pasting in progress and fragment not displayed -> must display the floating progress
                floatingProgressMinimize(false);
            }
        }
    }

    /**
     * Implements OnGlobalLayoutListener.
     * Goal is to setup the layout transition once the layout is actually done to have access to the width and/or height of the panel container view
     */
    @Override
    public void onGlobalLayout() {
        if (mPanelsContainer.getLayoutTransition() != null) {
            // layout transition already setup (onGlobalLayout() is called at each layout transition!)
            return;
        }
        LayoutTransition lt = new LayoutTransition();
        if (mPanelsContainer.getOrientation() == LinearLayout.HORIZONTAL) {
            final int height = mPanelsContainer.getHeight();
            lt.setAnimator(LayoutTransition.APPEARING,    ObjectAnimator.ofFloat(null, "TranslationY", height, 0));
            lt.setAnimator(LayoutTransition.DISAPPEARING, ObjectAnimator.ofFloat(null, "TranslationY", 0, height));
        }
        else if (mPanelsContainer.getOrientation() == LinearLayout.VERTICAL){
            final int width = mPanelsContainer.getWidth();
            lt.setAnimator(LayoutTransition.APPEARING,    ObjectAnimator.ofFloat(null, "TranslationX", width, 0));
            lt.setAnimator(LayoutTransition.DISAPPEARING, ObjectAnimator.ofFloat(null, "TranslationX", 0, width));
        }

        //lt.setDuration(1000); //for debug only
        lt.addTransitionListener(new TransitionListener() {
            public void startTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                //if (DBG) Log.d(TAG, "startTransition "+transitionType+" "+view);
            }

            public void endTransition(LayoutTransition transition, ViewGroup container, View view, int transitionType) {
                //if (DBG) Log.d(TAG, "endTransition "+transitionType+" "+view);
                // Make sure the call back is about one of the two panels
                final View focusedContainer = (mFocusedPanelFragment == null) ? null : getPanelFragmentContainer(mFocusedPanelFragment);
                final View defocusedContainer = (mDefocusedPanelFragment == null) ? null : getPanelFragmentContainer(mDefocusedPanelFragment);
                if ((view != focusedContainer) && (view != defocusedContainer)) {
                    return;
                }
                // Update the active state at the end of the front panel appearing animation
                if (transitionType == LayoutTransition.APPEARING) {
                    mFocusedPanelFragment.setActive(true, true);
                    mDefocusedPanelFragment.setActive(false, true);
                    invalidateOptionsMenu();
                }
            }
        });
        mPanelsContainer.setLayoutTransition(lt);
    }

    private void drawerLayoutSetup(Bundle savedInstanceState) {
        // This allow to get the activity onBackPressed() called, see http://stackoverflow.com/questions/18293726/android-onbackpressed-not-being-called-when-navigation-drawer-open
        mDrawer.setFocusableInTouchMode(false);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar application icon.
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawer,  R.string.navigation_drawer_open, R.string.navigation_drawer_close ) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                /* old code really needed ??? if (!mDrawer.isAdded()) {
                    return;
                }*/
                if (!mUserLearnedDrawer) {
                    // The user manually opened the drawer; store this flag to prevent auto-showing the navigation drawer automatically in the future.
                    mUserLearnedDrawer = true;
                    PreferenceManager.getDefaultSharedPreferences(RootActivity.this).edit().putBoolean(PREF_USER_LEARNED_DRAWER, true).apply();
                }
            }
        };

        // If the user hasn't learned about the drawer, open it to introduce it to the drawer, per the navigation drawer design guidelines.
        boolean openAtInit = false;
        mUserLearnedDrawer = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_USER_LEARNED_DRAWER, false);
        if (!mUserLearnedDrawer) {
            openAtInit = true;
        }
        if (savedInstanceState!=null) {
            openAtInit = savedInstanceState.getBoolean("drawerOpen", openAtInit);
        }
        if (openAtInit) {
            mDrawer.openDrawer(findViewById(R.id.source_drawer));
        }

        // Defer code dependent on restoration of previous instance state.
        mDrawer.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });
        mDrawer.setDrawerListener(mDrawerToggle);
    }

    /**
     * Open sliding menu
     * @return true is sliding menu was closed before
     */
    private boolean openDrawer() {
        if (!isDrawerOpen()) {
            mDrawer.openDrawer(mSourceFragment.getView());
            return true;
        }
        return false;
    }

    private boolean isDrawerOpen() {
        if (mDrawer != null) {
            return mDrawer.isDrawerOpen(mSourceFragment.getView());
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        if(mFileCopyFragment!=null) {
            hideCopyFragment(true);
            return;
        }
        if (!mFocusedPanelFragment.goBackOneLevel()) {
            if (mDrawer!=null && !isDrawerOpen()) {
                openDrawer();
            } else {
                finish();
            }
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.action_single_mode).setIcon(R.drawable.ic_menu_single).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.findItem(R.id.action_double_mode).setIcon(R.drawable.ic_menu_double).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.findItem(R.id.action_samba_password).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_single_mode).setVisible( mIsDoublePanel && mFileCopyFragment == null);
        menu.findItem(R.id.action_double_mode).setVisible(!mIsDoublePanel && mFileCopyFragment == null);
        menu.findItem(R.id.action_samba_password).setVisible(NetworkCredentialsDatabase.getInstance().getAllPersistentCredentials().size() > 0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // First check if the drawer toggle consumes the event
        if (mDrawerToggle!=null) {
            if (mDrawerToggle.onOptionsItemSelected(item)) {
                return true;
            }
        }

        switch (item.getItemId()) {

            case R.id.action_samba_password:
                Intent intent = new Intent(this,CredentialsManagerPreferenceActivity.class);
                startActivity(intent);
                return true;

            case R.id.action_single_mode:
                // Just hide the container of the fragment to hide
                BasePanelFragment fragmentToHide = mDefocusedPanelFragment;
                getPanelFragmentContainer(fragmentToHide).setVisibility(View.GONE);
                mIsDoublePanel = false;
                notifyPanelModeListeners();
                invalidateOptionsMenu(); // update single/double option menu item
                return true;

            case R.id.action_double_mode:
                // If the second panel already exists, just make it visible
                if (mDefocusedPanelFragment != null) {
                    // just show it again
                    getPanelFragmentContainer(mDefocusedPanelFragment).setVisibility(View.VISIBLE);
                    // Give focus to the newly displayed fragment
                    BasePanelFragment tmp = mFocusedPanelFragment;
                    mFocusedPanelFragment = mDefocusedPanelFragment;
                    mDefocusedPanelFragment = tmp;
                }
                // Else we need to create the fragment and to make it visible
                else {
                    // new fragment is always a local storage one
                    LocalListingFragment newFragment = new LocalListingFragment();
                    newFragment.setSourceInfo(new SourceInfo("file://"
                            + Environment.getExternalStorageDirectory().getPath()
                            + "/", getString(R.string.internal_storage)));
                    // Update focus pointers
                    mDefocusedPanelFragment = mFocusedPanelFragment;
                    mFocusedPanelFragment = newFragment;
                    // Make the container visible
                    ViewGroup container = getTheOtherFragmentContainer(getPanelFragmentContainer(mDefocusedPanelFragment));
                    container.setVisibility(View.VISIBLE);
                    // Add the fragment to the container
                    getSupportFragmentManager().beginTransaction()
                    .replace(container.getId(), newFragment)
                    .commit();
                }
                mIsDoublePanel = true;
                notifyPanelModeListeners();
                invalidateOptionsMenu(); // update single/double option menu item
                //update source button
                if (mFocusedPanelFragment instanceof NetworkRootFragment) {
                    mSourceFragment.onNetworkSelected(false);
                }
                else if (mFocusedPanelFragment instanceof LocalListingFragment) {
                    mSourceFragment.onSourceButtonSelected(((LocalListingFragment) mFocusedPanelFragment).getSourceInfo(), false);
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private OnDragListener mPanelFocusDragListener = new OnDragListener() {
        public boolean onDrag(View view, DragEvent event) {
            boolean dragEventHandled = false;

            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_STARTED");
                    dragEventHandled = true;
                    break;

                case DragEvent.ACTION_DRAG_ENTERED:
                    if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_ENTERED");
                    final View defocusedContainer = (mDefocusedPanelFragment != null) ? getPanelFragmentContainer(mDefocusedPanelFragment) : null;
                    if (defocusedContainer !=  null && view.equals(defocusedContainer)) {
                        swapPanelFocus();
                    }
                    break;
            }
            return dragEventHandled;
        }
    };

    private void swapPanelFocus() {
        BasePanelFragment tmp = mFocusedPanelFragment;
        mFocusedPanelFragment = mDefocusedPanelFragment;
        mDefocusedPanelFragment = tmp;

        // CAUTION: Important to call the "false" before the "true" for the ActionMode selection
        mDefocusedPanelFragment.setActive(false, true);
        mFocusedPanelFragment.setActive(true, true);

        if (mFocusedPanelFragment instanceof NetworkRootFragment) {
            mSourceFragment.onNetworkSelected(false);
        }
        else if (mFocusedPanelFragment instanceof LocalListingFragment) {
            mSourceFragment.onSourceButtonSelected(((LocalListingFragment) mFocusedPanelFragment).getSourceInfo(), false);
        }
        invalidateOptionsMenu();
    }


    /** Implements SourceInterface */
    @Override
    public void onNetworkSelected(boolean hovered) {
        if (hovered) {
            onSourceHovered(null, NetworkRootFragment.class);
        } else {
            onSourceSelected(null, NetworkRootFragment.class);
        }
    }

    /** Implements SourceInterface */
    @Override
    public void onSourceButtonSelected(SourceInfo sourceInfo, boolean hovered) {
        if (hovered) {
            onSourceHovered(sourceInfo,LocalListingFragment.class);
        } else {
            onSourceSelected(sourceInfo, LocalListingFragment.class);
        }
    }

    private void onSourceSelected(SourceInfo sourceInfo, Class<? extends BasePanelFragment> sourceFragmentClass) {
        // If this kind of fragment is already the current one, tap on source makes it go to its root
        if (sourceFragmentClass.isInstance(mFocusedPanelFragment)) {
            if(sourceInfo!=null&&sourceFragmentClass.equals(LocalListingFragment.class)){
                ((LocalListingFragment)mFocusedPanelFragment).setSourceInfo(sourceInfo);
            }

            mFocusedPanelFragment.goBackToRoot();
        }
        // Else we create a new fragment and replace the current one
        else {
            BasePanelFragment f;
            try {
                f = sourceFragmentClass.newInstance();
                if(sourceInfo!=null&&sourceFragmentClass.equals(LocalListingFragment.class)){
                    ((LocalListingFragment)f).setSourceInfo(sourceInfo);
                }
            } catch (Exception e) {
                throw new AssertionError("Failed to instanciate a "+sourceFragmentClass.getName(), e);
            }
            loadFragmentInCurrentPanel(f);
        }

        if (mDrawer!=null) {
            mDrawer.closeDrawers();
        }
    }

    private  void onSourceHovered(SourceInfo sourceInfo, Class<? extends BasePanelFragment> sourceFragmentClass) {
        // Bring the destination panel close to the source panel while the user is dragging files
        if (DBG) Log.d(TAG, "onSourceHovered double panel mode ="  + mIsDoublePanel);

        if (mIsDoublePanel) {
            //--------------------------------------------------------------------------
            // Both panels are already visible
            // => make sure the destination panel is active and displayed close to
            //    the source panel (needed for dragging files)
            // so bring the destination panel close to source panel
            //--------------------------------------------------------------------------

            if (mInitialDragView != null) {
                final ViewGroup focusedContainer = getPanelFragmentContainer(mFocusedPanelFragment);
                if (mInitialDragView.equals(mPanelOneContainer)) {
                    // The initial panel is active => swap the panel focus
                    if (mPanelOneContainer.equals(focusedContainer)) {
                        swapPanelFocus();
                    }
                    // need to move fragment to second panel ...
                } else {
                    // The initial panel is active => swap the panel focus
                    if (mPanelTwoContainer.equals(focusedContainer)) {
                        swapPanelFocus();
                    }
                }
            }
            onSourceSelected(sourceInfo, sourceFragmentClass);

        } else {
            //--------------------------------------------------------------------------
            // Only the initial panel is visible
            // => switch to double panel mode (panel will be automatically displayed
            // close to the source panel)
            // This will allow to drag directly the files to the active panel when
            // moving the finger from the source panel
            //--------------------------------------------------------------------------

            if (DBG) Log.d(TAG, "Switching to double panel mode");
            if (mDefocusedPanelFragment != null) {
                // The second panel already exists, just make it visible
                getPanelFragmentContainer(mDefocusedPanelFragment).setVisibility(View.VISIBLE);
                // Give focus to the newly displayed fragment
                BasePanelFragment tmp = mFocusedPanelFragment;
                mFocusedPanelFragment = mDefocusedPanelFragment;
                mDefocusedPanelFragment = tmp;
                mIsDoublePanel = true;
                notifyPanelModeListeners();
                invalidateOptionsMenu(); // update single/double option menu item
                onSourceSelected(sourceInfo, sourceFragmentClass);
            } else {
                // new fragment is always a local storage one
                BasePanelFragment newFragment;
                try {
                    newFragment = sourceFragmentClass.newInstance();
                } catch (Exception e) {
                    throw new AssertionError("Failed to instanciate a "+ sourceFragmentClass.getName(), e);
                }
                // Update focus pointers
                mDefocusedPanelFragment = mFocusedPanelFragment;
                mFocusedPanelFragment = newFragment;
                // Make the container visible
                ViewGroup container = getTheOtherFragmentContainer(getPanelFragmentContainer(mDefocusedPanelFragment));
                container.setVisibility(View.VISIBLE);
                // Add the fragment to the container
                getSupportFragmentManager().beginTransaction()
                .replace(container.getId(), newFragment)
                .commit();
                mIsDoublePanel = true;
                notifyPanelModeListeners();
                invalidateOptionsMenu(); // update single/double option menu item
            }
        }
    }

    /** Implements SourceInterface */
    @Override
    public void onDragStarted() {
        // Remember on which panel we started to drag
        mInitialDragView = getPanelFragmentContainer(mFocusedPanelFragment);
        if (DBG) Log.d(TAG, "onDragStarted : current active panel is " + (mInitialDragView.equals(mPanelOneContainer) ? "Panel one" : "Panel two"));
    }

    @Override
    public void onSourceUnmounted(SourceInfo unmountedSource, SourceInfo fallbackSource) {
        if(mFocusedPanelFragment instanceof LocalListingFragment&&((LocalListingFragment)mFocusedPanelFragment).getSourceInfo().equals(unmountedSource)){
            ((LocalListingFragment)mFocusedPanelFragment).setSourceInfo(fallbackSource);
            mFocusedPanelFragment.goBackToRoot();
            mSourceFragment.onSourceButtonSelected(fallbackSource, false);
        }
        if(mDefocusedPanelFragment instanceof LocalListingFragment&&((LocalListingFragment)mDefocusedPanelFragment).getSourceInfo().equals(unmountedSource)){
            ((LocalListingFragment)mDefocusedPanelFragment).setSourceInfo(fallbackSource);
            mDefocusedPanelFragment.goBackToRoot();
        }
        Log.d(TAG, "onSourceUnmounted :  " +unmountedSource.mRootPath);
    }

    private void loadFragmentInCurrentPanel(BasePanelFragment fragment) {
        ViewGroup focusedContainer = null;
        if (mFocusedPanelFragment!=null) {
            focusedContainer = getPanelFragmentContainer(mFocusedPanelFragment);
        } else {
            focusedContainer = mPanelOneContainer; // default
        }

        mFocusedPanelFragment = fragment;
        getSupportFragmentManager()
        .beginTransaction()
        .replace(focusedContainer.getId(), fragment)
        .commit();
    }

    /**
     * Implements NavigationInterface
     */
    @Override
    public void focusRequest(BasePanelFragment fragmentRequestingFocus) {
        if (fragmentRequestingFocus.equals(mDefocusedPanelFragment)) {
            swapPanelFocus();
        }
    }

    /**
     * A very basic straightforward utility method
     * @param thisOne
     * @return
     */
    private ViewGroup getPanelFragmentContainer(BasePanelFragment f) {
        if (f.equals(getSupportFragmentManager().findFragmentById(R.id.f1))) {
            return mPanelOneContainer;
        }
        else if (f.equals(getSupportFragmentManager().findFragmentById(R.id.f2))) {
            return mPanelTwoContainer;
        }
        else {
            Log.e(TAG, "The given BasePanelFragment is neither in f1 nor in f2!");
            return null;
        }
    }

    /**
     * A very basic straightforward utility method
     * @param thisOne
     * @return
     */
    private ViewGroup getTheOtherFragmentContainer(ViewGroup thisOne) {
        if (thisOne.equals(mPanelOneContainer)) {
            return mPanelTwoContainer;
        }
        else if (thisOne.equals(mPanelTwoContainer)) {
            return mPanelOneContainer;
        }
        else {
            throw new IllegalStateException("The given ViewGroup must be mPanelOneContainer or mPanelTwoContainer!");
        }
    }

    @Override
    public android.app.FragmentManager getFragmentManager() {
        throw new IllegalStateException("DO NOT USE THIS IN THIS ACTIVITY, USE getSupportFramgentManager instead!");
    }

    private void launchCopyInfoFragment() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (FileManagerService.fileManagerService != null
                    && (FileManagerService.fileManagerService.isActionRunning())) {
                    displayCopyFragment(false);
                }
            }
        }, COPY_INFO_LAUNCH_DELAY_MS);
    }

    private void displayCopyFragment(boolean enlargeAnimation){
        if(mFileCopyFragment==null){
            mFileCopyFragment = new FileCopyFragment();
            mFileCopyFragment.setListener(mFileCopyFragmentListener);
        }
        floatingProgressEnlarge();
        displayContextFragment(mFileCopyFragment, enlargeAnimation);
    }

    private void displayContextFragment(Fragment info, boolean enlargeAnimation){
        mFocusedPanelFragment.setActive(false, true);
        mInfoContainer.setVisibility(View.VISIBLE);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if(enlargeAnimation) {
            ft.setCustomAnimations(R.anim.context_fragment_in_animation, 0);
        } else {
            ft.setCustomAnimations(R.anim.context_fragment_slide_in_from_bottom, 0);
        }
        ft.replace(R.id.info_container,info);
        ft.commit();
        invalidateOptionsMenu();
    }

    private void hideCopyFragment(boolean minimizeAnimation){
        if(mFileCopyFragment!=null){
            hideContextFragment(mFileCopyFragment, minimizeAnimation);
            mFileCopyFragment=null;
        }
        if(FileManagerService.fileManagerService!=null && (FileManagerService.fileManagerService.isActionRunning())){
            floatingProgressMinimize(true);
        }
    }

    private void hideContextFragment(Fragment info, boolean minimizeAnimation) {
        mFocusedPanelFragment.setActive(true, true);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        if (minimizeAnimation) {
            ft.setCustomAnimations(0, R.anim.context_fragment_out_animation);
        } else {
            ft.setCustomAnimations(0, R.anim.context_fragment_slide_out_to_bottom);
        }
        ft.remove(info);
        ft.commit();
        invalidateOptionsMenu();
    }

    private void floatingProgressMinimize(boolean animated) {
        if (!animated) {
            // Better force clean all the properties we are animating
            mProgressDrawable.setScaleX(1);
            mProgressDrawable.setScaleY(1);
            mProgressDrawable.setTranslationX(0);
            mProgressDrawable.setTranslationY(0);
            mProgressDrawable.setAlpha(1f);

            mProgressDrawable.setVisibility(View.VISIBLE);
        }
        else {
            if (mProgressDrawable.getVisibility() == View.GONE) {
                mProgressDrawable.setScaleX(4);
                mProgressDrawable.setScaleY(4);
                mProgressDrawable.setTranslationX(getResources().getDimension(R.dimen.floating_progress_enlarge_translation_x));
                mProgressDrawable.setTranslationY(getResources().getDimension(R.dimen.floating_progress_enlarge_translation_y));
                mProgressDrawable.setAlpha(0f);
            }
            mProgressDrawable.setVisibility(View.VISIBLE);
            mProgressDrawable.animate().translationX(0).translationY(0).scaleX(1f).scaleY(1f).alpha(1f);
        }
    }

    private void floatingProgressEnlarge() {
        mProgressDrawable.animate()
                .translationX(getResources().getDimension(R.dimen.floating_progress_enlarge_translation_x))
                .translationY(getResources().getDimension(R.dimen.floating_progress_enlarge_translation_y))
                .scaleX(4f).scaleY(4f)
                .alpha(0f)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mProgressDrawable.setVisibility(View.GONE);
                    }
                });
    }

    private void floatingProgressHide() {
        mProgressDrawable.animate().translationX(0).translationY(0).scaleX(0).scaleY(0).alpha(0f)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mProgressDrawable.setVisibility(View.GONE);
                    }
                });
    }

    @Override
    public void onActionStart() {
        launchCopyInfoFragment();
    }

    @Override
    public void onProgressUpdate() {}

    @Override
    public void onActionStop() {
        if (mFileCopyFragment == null) {
            int message = -1;
            switch (FileManagerService.fileManagerService.getActionMode()) {
                case COPY:
                    if (FileManagerService.fileManagerService.getPasteTotalFiles() == 1)
                        message = R.string.copy_file_success_one;
                    else
                        message = R.string.copy_file_success_many;
                    break;
                case CUT:
                    if (FileManagerService.fileManagerService.getPasteTotalFiles() == 1)
                        message = R.string.cut_file_success_one;
                    else
                        message = R.string.cut_file_success_many;
                    break;
                case DELETE:
                    message = R.string.delete_done;
                    break;
                case COMPRESSION:
                    message = R.string.zip_compressing_success;
                    break;
                case EXTRACTION:
                    message = R.string.zip_extract_success_message;
                    break;
            }
            if (message != -1) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        }

        floatingProgressHide();
    }

    @Override
    public void onActionError() {
        floatingProgressHide();
    }

    @Override
    public void onActionCanceled() {
        floatingProgressHide();
    }

}
