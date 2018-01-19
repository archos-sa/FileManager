package com.archos.filemanager.listing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.ListingEngineFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.filecorelibrary.Utils;
import com.archos.filecorelibrary.ListingEngine.ErrorEnum;
import com.archos.filecorelibrary.ListingEngine.SortOrder;
import com.archos.filecorelibrary.zip.ZipUtils;
import com.archos.filemanager.BasePanelFragment;
import com.archos.filemanager.BreadCrumbTrailView;
import com.archos.filemanager.FileManagerService;
import com.archos.filemanager.FileManagerUtils;
import com.archos.filemanager.NewFileOrDirectoryDialog;
import com.archos.filemanager.FileManagerService.ServiceListener;
import com.archos.filemanager.PasteAndDragBin;
import com.archos.filemanager.PermissionChecker;
import com.archos.filemanager.R;
import com.archos.filemanager.listing.FileAdapter.OnFileClickListener;
import com.archos.filemanager.listing.FileAdapter.OnFileLongClickListener;
import com.archos.filemanager.listing.ListingCache.SavedData;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnItemTouchListener;
import android.util.Log;
import android.util.SparseArray;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;


public abstract class ListingFragment extends BasePanelFragment implements ListingEngine.Listener, BreadCrumbTrailView.OnSegmentClickListener, ServiceListener, OnDragListener, OnItemTouchListener {

    protected static final String TAG = "ListingFragment";
    private static final boolean DBG = false;

    public static final String ACTION_REFRESH_LISTING_FRAGMENT = "action_refresh_listing_fragment";
    public static final String URI_TO_REFRESH = "uri_to_refresh";
    public static final String PARENT_NEED_TO_BE_REFRESHED = "parent_need_to_be_refreshed";

    private MetaFile2 mCurrentMetaFile;
    private MetaFileRetriever mMetaFileRetriever;
    protected Stack<String> mUriStack;
    private View mRetryButton;
    private BroadcastReceiver mReceiver;

    private class MetaFileRetriever {
        public void retrieve(final Uri uri) {
            new AsyncTask<Void, Void, MetaFile2>() {
                @Override
                protected MetaFile2 doInBackground(Void... voids) {
                    try {
                        MetaFile2 ret = MetaFile2Factory.getMetaFileForUrl(uri);
                        if (uri.equals(mCurrentUri)) {
                            mCurrentMetaFile = ret;
                            return ret;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(MetaFile2 result) {
                    if (result != null && getActivity() != null) {
                        // refresh option menu to check if we can write in current folder (for paste menu)
                        getActivity().invalidateOptionsMenu();
                    }
                }
            }.execute();
        }
    }

    protected enum State {
        /** Just created, need to load data */
        INIT,

        /** Not loading */
        IDLE,

        /** Loading: listing engine is either running or delayed in the handler queue */
        LOADING,
    }

    private static int LIST_SCROLL_PERIOD = 40;             // update period in ms when scrolling
    private static int LISTING_SCROLL_MAX_SPEED = 40;       // maximum increment in pixels per update
    private static int LIST_SCROLL_INITIAL_DELAY = 500;     // delay in ms before the ListView starts scrolling
    public  static float VALID_MOVE_EVENT_THRESHOLD = 4.0f; // ignore smaller moves while dragging files
    private static int VALID_DRAG_EVENT_THRESHOLD = 5;

    private static int DRAG_ENTER_SUBFOLDER_INITIAL_DELAY = 1000; // delay in ms before entering automatically a subfolder when dragging files
    // (must be longer than LIST_SCROLL_INITIAL_DELAY so that scrolling will be prioritary)

    private static final int PASTE_OK = 0;
    private static final int PASTE_ERR_FOLDER = 1;

    private static int POPUP_MIN_WIDTH = 200;

    private static SparseArray<SortOrder> sortOrderAscending= new SparseArray<SortOrder>();
    static {
        sortOrderAscending.put(R.id.sort_by_name, SortOrder.SORT_BY_NAME_ASC);
        sortOrderAscending.put(R.id.sort_by_size, SortOrder.SORT_BY_SIZE_ASC);
        sortOrderAscending.put(R.id.sort_by_date, SortOrder.SORT_BY_DATE_ASC);
    }

    private static SparseArray<SortOrder> sortOrderDescending= new SparseArray<SortOrder>();
    static {
        sortOrderDescending.put(R.id.sort_by_name, SortOrder.SORT_BY_NAME_DESC);
        sortOrderDescending.put(R.id.sort_by_size, SortOrder.SORT_BY_SIZE_DESC);
        sortOrderDescending.put(R.id.sort_by_date, SortOrder.SORT_BY_DATE_DESC);
    }

    private View mLoadingProgress;
    private View mLoadingVeil;
    private View mEmptyView; // contains mEmptyTextView, this is the view whose visibility will be changed
    private TextView mEmptyTextView; // empty message
    private RecyclerView mList;
    private LinearLayoutManager mLayoutManager;
    private FileAdapter mAdapter;
    protected BreadCrumbTrailView mBreadCrumbTrailerView;
    private Parcelable mRefreshingLayoutState;// List position when refreshing the list. Not saved in cache because cache is made to save list of files
    protected Uri mCurrentUri;
    private SortOrder mSortOrder;

    private int mTouchX; // The x coordinate of any touch event; used to ensure the drag overlay is drawn correctly
    private int mTouchY; // The y coordinate of any touch event; used to ensure the drag overlay is drawn correctly
    private int mDraggableItemPosition = -1;
    private int mListTopOffset;
    private float mPreviousDragEventX;
    private float mPreviousDragEventY;
    private boolean mDragLocked = false;
    private int mInitialDragPosition = -1;
    private int mHoveredItemPosition = -1;
    private boolean mIsInsideHoveredItemDroppableArea = false;
    private ListingScrollStatus mScrollStatus;
    private boolean mPopupValidated;

    /**
     * True if the instance is being restored from a previous instance (screen rotation for example)
     */
    private boolean mRestoringInstance = false;

    private boolean mPreviousInstanceWasLoading = false;

    final private int mListAnimationDuration = 100;
    private int mListAnimationTranslation;

    private BrowsingDirection mBrowsingDirection = BrowsingDirection.UNKNOWN;

    /**
     * For now this state is only about "loading" or "not loading"
     */
    protected State mState = State.INIT;

    /**
     * Handler used to delay some actions for animation purpose.
     * It runs on the main thread.
     */
    final private Handler mHandler = new Handler();

    /**
     * We will get a new ListingEngine instance each time we need to list, but we need to keep a reference to it to cancel it in some cases
     */
    private ListingEngine mEngine = null;

    /**
     * Cache containing the listing (and position) we got for the parent Uris.
     */
    private ListingCache mCache = new ListingCache();

    /**
     * Handle selection of MetaFile2 and display the related ActionMode items
     */
    private MetaFileActionModeManager mActionModeManager;

    /**
     * Give Uri to start browsing from.
     * Also goBackOneLevel() will return false if this root Uri is the current one
     */
    abstract protected Uri getStartingUri();

    /**
     * goBackOneLevel() will return false if this root Uri is the current one
     */
    abstract protected Uri getRootUri();

    /**
     * Give the name to display for the root
     */
    abstract protected String getRootName(Context context);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUriStack = new Stack<String>();
        mListAnimationTranslation = getResources().getDimensionPixelSize(R.dimen.list_animation_translation);
        mScrollStatus = new ListingScrollStatus();
        mMetaFileRetriever = new MetaFileRetriever();

        setHasOptionsMenu(true);
        mCurrentUri = getStartingUri();
        mSortOrder = SortOrder.SORT_BY_NAME_ASC;
        // Get "saved instance" parameters
        if (savedInstanceState != null) {
            mRestoringInstance = true;
            Log.d(TAG, "savedInstanceState!=null");

            // Check if the saved instance was loading
            State saveState = State.values()[savedInstanceState.getInt("mState", 0)];
            mPreviousInstanceWasLoading = (saveState == State.LOADING);
            if (savedInstanceState.getSerializable("mUriStack") != null) {
                // for a reason I don't understand, some device are converting stack into arraylist when serializing and some doesn't
                if (savedInstanceState.getSerializable("mUriStack") instanceof Stack) {
                    mUriStack = (Stack<String>) savedInstanceState.getSerializable("mUriStack");
                } else {
                    mUriStack.addAll((List<String>) savedInstanceState.getSerializable("mUriStack"));
                }
            }
            mCurrentUri = (Uri)savedInstanceState.getParcelable("mCurrentUri");
        }
        else {
            mRestoringInstance = false;
            Log.d(TAG, this + "mRestoringInstance=" + mRestoringInstance);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Cancel the ActionMode when quitting this fragment.
        // NOTE: MetaFileActionModeManager is smart enough to not clear the file selection if onDestroy is called for a screen rotation
        mActionModeManager.hide();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (isActive()) {
            menu.add(0,R.string.refresh,20,R.string.refresh).setIcon(R.drawable.ic_menu_refresh).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, android.R.string.paste, 20, android.R.string.paste).setIcon(R.drawable.ic_menu_file_paste).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, android.R.string.selectAll, 20, android.R.string.selectAll).setIcon(R.drawable.ic_menu_select_all);
            menu.add(0, R.string.new_folder_or_file, 20,  R.string.new_folder_or_file).setIcon(R.drawable.ic_menu_new_directory).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, R.string.listing_sort_mode, 20, R.string.listing_sort_mode).setIcon(R.drawable.ic_menu_sort).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        if (isActive()) {
            menu.findItem(android.R.string.paste).setVisible(PasteAndDragBin.getPastebinMetafiles().size() > 0
                    && getCurrentMetaFile() != null
                    && !ZipUtils.isZipMetaFile(getCurrentMetaFile())
                    && (getCurrentMetaFile().canWrite())); // do not paste in zip files. Write can be true because the file itself can be modified
            menu.findItem(android.R.string.selectAll).setVisible(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (isActive()) {
            switch(item.getItemId()) {
                case android.R.string.paste:
                    if (PasteAndDragBin.getPastebinMetafiles().size() > 0) {
                        if (FileManagerService.fileManagerService != null) {
                            List<MetaFile2> tmp = new ArrayList<>(PasteAndDragBin.getPastebinMetafiles());
                            if (PasteAndDragBin.currentPasteMode == FileManagerService.FileActionEnum.COPY) {
                                FileManagerService.fileManagerService.copy(tmp, mCurrentUri);
                            } else {
                                FileManagerService.fileManagerService.cut(tmp, mCurrentUri);
                                PasteAndDragBin.clearPastebin();
                            }
                        }
                    }
                    return true;

                case android.R.string.selectAll:
                    mAdapter.selectAll();
                    return true;

                case R.string.refresh:
                    refresh();
                    return true;

                case R.string.new_folder_or_file:
                    NewFileOrDirectoryDialog rd = new NewFileOrDirectoryDialog();
                    Bundle renameArgs = new Bundle();
                    renameArgs.putParcelable(NewFileOrDirectoryDialog.URI, mCurrentUri);
                    rd.setArguments(renameArgs);
                    rd.show(getActivity().getSupportFragmentManager(), rd.getClass().getName());
                    return true;

                case R.string.listing_sort_mode:
                    View anchorView = getActivity().findViewById(R.id.action_double_mode);
                    if (anchorView == null) {
                        anchorView = getActivity().findViewById(R.id.action_single_mode);
                    }
                    final PopupMenu popup = new PopupMenu(getActivity(), anchorView);
                    final Menu menu = popup.getMenu();
                    popup.getMenuInflater().inflate(R.menu.sort_mode_menu, menu);
                    menu.findItem(getCurrentSortBySelection()).setChecked(true);
                    menu.findItem(sortOrderAscending.indexOfValue(mSortOrder) >= 0 ? R.id.ascending : R.id.descending).setChecked(true);
                    popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        public boolean onMenuItemClick(MenuItem item) {
                            final SortOrder sortOrder;
                            if (item.getGroupId() == R.id.group_sort_by) {
                                if (menu.findItem(R.id.ascending).isChecked()) {
                                    sortOrder = sortOrderAscending.get(item.getItemId());
                                } else {
                                    sortOrder = sortOrderDescending.get(item.getItemId());
                                }
                            } else {
                                final int selectedId = getCurrentSortBySelection();
                                if (item.getItemId() == R.id.ascending) {
                                    sortOrder = sortOrderAscending.get(selectedId);
                                } else {
                                    sortOrder = sortOrderDescending.get(selectedId);
                                }
                            }
                            if (mSortOrder != sortOrder) {
                                mSortOrder = sortOrder;
                                refresh();
                            }
                            return true;
                        }
                    });
                    popup.show();
                    return true;
            }
        }
        return false;
    }

    private int getCurrentSortBySelection() {
        if (sortOrderAscending.indexOfValue(mSortOrder) >= 0) {
            return sortOrderAscending.keyAt(sortOrderAscending.indexOfValue(mSortOrder));
        } else {
            return sortOrderDescending.keyAt(sortOrderDescending.indexOfValue(mSortOrder));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, this + "onSaveInstanceState");

        outState.putParcelable("mCurrentUri", mCurrentUri);
        outState.putParcelable("mLayoutManager", mLayoutManager.onSaveInstanceState()); // Save the layout manager state (that's cool we don't even know what it is doing inside!)
        // Save details of the empty view. In the future we may need to define various error states instead
        outState.putInt("mEmptyView.getVisibility()", mEmptyView.getVisibility());
        outState.putInt("mEmptyTextView.getTextColor()", mEmptyTextView.getCurrentTextColor());
        outState.putCharSequence("mEmptyTextView.getText()", mEmptyTextView.getText());
        outState.putSerializable("mUriStack", mUriStack);
        // If the engine is currently loading at this point, we remember it. It will be restarted if the fragment is recreated from this saved state.
        // NOTE: this onSaveInstanceState() is called before onStop and onDestroy
        outState.putInt("mState", mState.ordinal());

        // Save the adapter "saved instance" parameters
        mAdapter.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.listing_fragment, container, false);
        mLoadingProgress = v.findViewById(R.id.loading_progress);
        mLoadingVeil = v.findViewById(R.id.loading_veil);
        mEmptyView = v.findViewById(R.id.empty_view);
        mRetryButton = v.findViewById(R.id.retry_button);
        mRetryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        mEmptyTextView = (TextView)v.findViewById(R.id.empty_textview);
        mList = (RecyclerView)v.findViewById(R.id.recycler_view);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_REFRESH_LISTING_FRAGMENT);
        filter.addAction(PermissionChecker.STORAGE_PERMISSION_GRANTED);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(PermissionChecker.STORAGE_PERMISSION_GRANTED) || action.equals(ACTION_REFRESH_LISTING_FRAGMENT)) {
                    Uri uriToRefresh = intent.getParcelableExtra(URI_TO_REFRESH);
                    boolean parentNeedToBeRefreshed = intent.getBooleanExtra(PARENT_NEED_TO_BE_REFRESHED, false);
                    refreshIfNeeded(uriToRefresh, parentNeedToBeRefreshed);
                }
            }
        };
        getActivity().registerReceiver(mReceiver, filter);

        mLayoutManager = new LinearLayoutManager(getActivity());
        mList.setLayoutManager(mLayoutManager);
        mList.setHasFixedSize(false); // name can go on more than one line hence some items are higher
        mList.setItemAnimator(new DefaultItemAnimator());
        mList.addItemDecoration(new DividerItemDecoration( getResources().getDrawable(R.drawable.listview_divider), DividerItemDecoration.VERTICAL_LIST));
        mList.addOnItemTouchListener(this);
        mList.setOnDragListener(this);

        mEmptyView.setVisibility(View.GONE);

        mAdapter = new FileAdapter(getActivity());
        mAdapter.setOnFileClickListener(new OnFileClickListener() {
            public void onFileClick(MetaFile2 file, View v) {
                ListingFragment.this.onFileClick(file,v);
            }
        });
        mAdapter.setOnFileLongClickListener(new OnFileLongClickListener() {
            public boolean onFileLongClick(MetaFile2 file, View v, int position) {
                mAdapter.selectFile(file, v);
                mDraggableItemPosition = position;
                return true;
            }
        });

        if (savedInstanceState != null) {
            mAdapter.onRestoreInstanceState(savedInstanceState); // Restore the adapter "saved instance" parameters
            mLayoutManager.onRestoreInstanceState(savedInstanceState.getParcelable("mLayoutManager")); // Restore the layout manager state
            // Restore the mEmptyView state
            // no inspection ResourceType
            mEmptyView.setVisibility(savedInstanceState.getInt("mEmptyView.getVisibility()"));
            mEmptyTextView.setTextColor(savedInstanceState.getInt("mEmptyTextView.getTextColor()"));
            mEmptyTextView.setText(savedInstanceState.getCharSequence("mEmptyTextView.getText()"));
        }
        mList.setAdapter(mAdapter);

        mBreadCrumbTrailerView = (BreadCrumbTrailView)v.findViewById(R.id.breadcrumbtrail_view);
        mBreadCrumbTrailerView.setRoot(getRootUri(), getRootName(getActivity()));
        mBreadCrumbTrailerView.addOnSegmentClickListener(this);

        if (!(getActivity() instanceof FragmentActivity)) {
            throw new IllegalStateException("The ListingFramgent/MetaFileActionModeManager is currently working with a FragmentActivity only!");
        }
        // Create and initialize the selection and ActionMode manager

        mActionModeManager = new MetaFileActionModeManager(mAdapter, this);
        mAdapter.setOnSelectionChangedListener(mActionModeManager);
        if (isActive()) {
            mActionModeManager.updateVisibility();
        } else {
            mActionModeManager.hide();
        }
        return v;
    }

    private Uri getParentUri(Uri uri) {
        Uri parentUri = Utils.getParentUrl(uri);
        if (parentUri != null) {
            String parent = parentUri.toString();
            if (parent != null && !parent.isEmpty() && parent.endsWith("/")) {
                int index = parent.lastIndexOf("/");
                if (index > 0) {
                    parent = parent.substring(0, index);
                }
            }
            return Uri.parse(parent);
        }
        return uri;
    }

    protected boolean onFileClick(MetaFile2 file, View v) { // can be override be children
        // Avoid weird double opening on stress test by forbidding to open an item while one is already "opening"
        if (mState == State.LOADING) {
            Log.d(TAG, "onFileClick does nothing because something is already loading");
            return false;
        }
        Log.d(TAG, "onFileClick Uri=" + file.getUri());

        if (file.isDirectory()) {
            // Store current list to cache
            mCache.put(mCurrentUri, mAdapter.getFiles(), mLayoutManager.onSaveInstanceState());
            // Start loading new Uri
            mUriStack.push(mCurrentUri.toString());
            mCurrentUri = file.getUri();
            mBrowsingDirection = BrowsingDirection.FORWARD;
            // We delay the listing a *little* bit to let the animation above go smoothly
            startListing(mCurrentUri, 100, file);
        }
        else if ("application/zip".equals(file.getMimeType())) {
            // Store current list to cache
            mCache.put(mCurrentUri, mAdapter.getFiles(), mLayoutManager.onSaveInstanceState());
            // Start loading new Uri
            mUriStack.push(mCurrentUri.toString());
            String uri = "zip://" + file.getUri().getPath();
            mCurrentUri = Uri.parse(uri);
            mBrowsingDirection = BrowsingDirection.FORWARD;
            // We delay the listing a *little* bit to let the animation above go smoothly
            startListing(mCurrentUri, 100, file);
        }
        else {
            if (Utils.isLocal(file.getUri())) {
                FileLauncher.openFile(file.getUri(), file.getExtension(), file.getMimeType(), getActivity());
            }
            else {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (FileManagerService.fileManagerService != null) {
            FileManagerService.fileManagerService.addListener(this);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        mBreadCrumbTrailerView.removeOnSegmentClickListener(this);
        // abort here instead of in onPause to allow the listing to finish in background
        abortCurrentAndDelayedListing();
        getActivity().unregisterReceiver(mReceiver);
        if (FileManagerService.fileManagerService != null) {
            FileManagerService.fileManagerService.deleteObserver(this);
        }
    }

    public ArrayList<Uri> getPreviousUris() {
        ArrayList<Uri> uris = new ArrayList<>();
        for (String uri : Arrays.asList(mUriStack.toArray(new String[mUriStack.size()]))) {
            uris.add(Uri.parse(uri));
        }
        return uris;
    }

    @Override
    public void onResume() {
        super.onResume();

        // No special loading animation for the initial load or reload
        mBrowsingDirection = BrowsingDirection.UNKNOWN;

        if (mRestoringInstance) {
            // hide progress UI stuff
            hideVeilAndProgress(false);
            // restore BreadCrumbTrailer
            mBreadCrumbTrailerView.setCurrentUri(mCurrentUri, getPreviousUris());

            // If have been destroyed while loading content for mCurrentUri we need to start loading it again
            if (mPreviousInstanceWasLoading) {
                startListing(mCurrentUri, 0, mCurrentMetaFile);
            }

            // Reset flag
            mRestoringInstance = false;
        }
        else if (mState == State.INIT && PermissionChecker.hasStoragePermission(getContext())) {
            // Start loading initial content
            startListing(mCurrentUri, 0, mCurrentMetaFile);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent event) {
	    boolean result = false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if(DBG) Log.d(TAG, "MotionEvent.ACTION_DOWN");
                // Save the initial touch location to draw the drag overlay at the correct location
                mTouchX = (int)event.getX();
                mTouchY = (int)event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                if(DBG) Log.d(TAG, "MotionEvent.ACTION_MOVE");
                if (mDraggableItemPosition >= 0) {
                    // An item is currently selected after a long-click
                    // => enter drag mode as soon as the user moves enough his finger
                    int deltaX = Math.abs((int)event.getX() - mTouchX);
                    int deltaY = Math.abs((int)event.getY() - mTouchY);
                    if (deltaX > VALID_DRAG_EVENT_THRESHOLD || deltaY > VALID_DRAG_EVENT_THRESHOLD) {
                        View item = mList.findChildViewUnder(mTouchX, mTouchY);
                        if (item != null) {
                            startDrag(mDraggableItemPosition, item, mTouchX);
                        }
                        result = true;
                    }
                }
            break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if(DBG) Log.d(TAG, "MotionEvent.ACTION_CANCEL or MotionEvent.ACTION_UP");
                mDraggableItemPosition = -1;
                break;
        }
        return result;
    }

	@Override
	public void onTouchEvent(RecyclerView rv, MotionEvent event) {
	}

    private void startDrag(int position, View item, int touchPointX) {
        if (getSelectedFiles() == null || getSelectedFiles().isEmpty()) {
            return;
        }
        String name;
        int iconResId;
        if (getSelectedFiles().size() > 1) {
            name = FileManagerUtils.getMultiFilesStringOneLine(getSelectedFiles());
            iconResId = R.drawable.filetype_multiple_files;
        } else {
            MetaFile2 selected = getSelectedFiles().get(0);
            name = selected.getName();
            iconResId = FileManagerUtils.getIconResIdForFile(selected);
        }
        item.startDrag(ClipData.newPlainText("File", name), new ItemDragShadowBuilder(getActivity(), item, touchPointX, name, iconResId), null, 0);

        // Remember where we started to drag
        mInitialDragPosition = position;
    }

    @Override
    public boolean onDrag(View view, DragEvent event) {
        boolean result = false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_STARTED");
                List<MetaFile2> selectedFiles = isActive() ? getSelectedFiles() : null;
                if (selectedFiles != null && selectedFiles.size() > 0) {
                    PasteAndDragBin.addToDragBin(selectedFiles);
                }

                mHoveredItemPosition = -1;
                mIsInsideHoveredItemDroppableArea = false;
                result = true;
                break;

            case DragEvent.ACTION_DRAG_ENTERED:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_ENTERED");
                mDraggableItemPosition = -1;
                mScrollStatus.init(mList.getHeight(), LISTING_SCROLL_MAX_SPEED);

                mPreviousDragEventX = (int)event.getX();
                mPreviousDragEventY = (int)event.getY();
                break;

            case DragEvent.ACTION_DRAG_EXITED:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_EXITED");
                mIsInsideHoveredItemDroppableArea = false;

                // Make sure to stop drag & drop pending actions
                mScrollStatus.reset();
                mHandler.removeCallbacks(mEnterSubfolderRunnable);
                mHandler.removeCallbacks(mScrollListRunnable);
                break;

            case DragEvent.ACTION_DRAG_ENDED:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_ENDED");
                // Drag & drop done
                mInitialDragPosition = -1;
                mHoveredItemPosition = -1;
                mIsInsideHoveredItemDroppableArea = false;
                break;

            case DragEvent.ACTION_DRAG_LOCATION:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_LOCATION");
                if (!mDragLocked) {
                    // Retrieve the coordinates of the current drag point
                    float x = event.getX();
                    float y = event.getY();
                    if (mPreviousDragEventX < 0 || mPreviousDragEventY < 0) {
                        // Resume dragging after entering automatically a subfolder
                        mPreviousDragEventX = (int)x;
                        mPreviousDragEventY = (int)y;
                    }

                    // Some touch-screens keep sending ACTION_DRAG_LOCATION events repeatedly
                    // even if the finger doesn't move => ignore these events if the drag point doesn't
                    // move (or only by a very small amount to filter the touchscreen erratic response)
                    if (Math.abs(x - mPreviousDragEventX) > VALID_MOVE_EVENT_THRESHOLD || Math.abs(y - mPreviousDragEventY) > VALID_MOVE_EVENT_THRESHOLD) {
                        if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_LOCATION at x = " + x + " - y = " + y);

                        // Update the display if needed
                        updateDragView((int)x, (int)y);

                        mScrollStatus.computeSpeedFromDragPosition((int)x, (int)y, mList.getChildCount() > 0);
                        if (mScrollStatus.isScrolling() && mScrollStatus.getSpeed() == 0) {
                            // The user moved his finger out of the top/bottom scrolling areas
                            // => stop scrolling immediately
                            mHandler.removeCallbacks(mScrollListRunnable);
                            mScrollStatus.stop();
                        }
                        else if (!mScrollStatus.isScrolling() && mScrollStatus.getSpeed() != 0) {
                            // The user moved its finger inside the top/bottom scrolling areas
                            // => this triggers the scrolling, but only after a short time if
                            // the user did not move his finger in the meantime:
                            // - if the user do not move its finger no ACTION_DRAG_LOCATION events will not
                            //   be received anymore so we must use a delayed message to request scrolling.
                            // - if ever the user moves its finger in the meantime more ACTION_DRAG_LOCATION
                            //   events will be received so cancel any previous request each time and send
                            //   another delayed scrolling request.
                            mListTopOffset = -1;
                            mHandler.removeCallbacks(mScrollListRunnable);
                            mHandler.postDelayed(mScrollListRunnable, LIST_SCROLL_INITIAL_DELAY);
                        }

                        if (!mScrollStatus.isScrolling()) {
                            if(DBG) Log.d(TAG, "Cancel pending Enter Subfolder and request a new one");
                            mHandler.removeCallbacks(mEnterSubfolderRunnable);
                            mHandler.postDelayed(mEnterSubfolderRunnable, DRAG_ENTER_SUBFOLDER_INITIAL_DELAY);
                        }
                    }

                    mPreviousDragEventX = x;
                    mPreviousDragEventY = y;
                }
                break;

            case DragEvent.ACTION_DROP:
                if(DBG) Log.d(TAG, "onDrag : ACTION_DROP");
                // Make sure scrolling is stopped
                mHandler.removeCallbacks(mScrollListRunnable);
                mScrollStatus.reset();

                // Do nothing in the following cases:
                //  - when dropping an item on itself in the same panel (this avoids the annoying
                //      "can not drop item on itself" message if dragging unintentionally an
                //      item by a few pixels when performing a long-click to select it)
                //  - when dropping an item in an empty area of the same panel.
                if (mInitialDragPosition >= 0 && (mHoveredItemPosition == -1 || mHoveredItemPosition == mInitialDragPosition)) {
                    // NOTE : mInitialDragPosition >= 0 => we are in the initial panel (where we started to drag)
                    //        mInitialDragPosition = -1 => we are in the other panel
                    return result;
                }

                dropInDirectory(view, event);
                break;

            default:
                break;
        }
        return result;
    }

    final private Runnable mListingRunnable = new Runnable() {
        public void run() {
            // abort previous engine one if there is any
            if (mEngine!=null) {
                mEngine.abort();
            }
            mEngine = ListingEngineFactory.getListingEngineForUrl(getActivity(), mCurrentUri);
            mEngine.setListener(ListingFragment.this);
            mEngine.setSortOrder(mSortOrder);
            mEngine.setKeepHiddenFiles(true);
            mEngine.setListingTimeOut(getListingTimeOut());
            mEngine.start();
        }
    };

    protected abstract long getListingTimeOut();

    final private Runnable mScrollListRunnable = new Runnable() {

        public void run() {
            int firstVisiblePosition = mLayoutManager.findFirstVisibleItemPosition();
            int lastVisiblePosition = mLayoutManager.findLastVisibleItemPosition();

            // Check first if we reached the top or bottom of the list
            if (mScrollStatus.getSpeed() != 0) {
                // Retrieve the vertical offset of the first visible item of the list
                int topOffset = mList.getChildAt(0).getTop();
                if (topOffset == mListTopOffset) {
                    // We are supposed to be scrolling but the vertical offset
                    // has not changed. This means that we are stuck at the top
                    // or the bottom of the list => stop scrolling
                    mScrollStatus.stop();
                    return;
                }
                mListTopOffset = topOffset;
            }

            if (!mScrollStatus.isScrolling()) {
                boolean topReached = (firstVisiblePosition == 0 && mList.getChildAt(0).getTop() >= 0);
                boolean bottomReached = (lastVisiblePosition >= mAdapter.getItemCount() - 1
                                         && mList.getChildAt(lastVisiblePosition - firstVisiblePosition).getBottom() <= mList.getHeight());

                if (!topReached && !bottomReached) {
                    // Scrolling is now starting
                    mScrollStatus.start();

                    // Entering subfolders is not allowed while scrolling
                    // so disable any corresponding pending request
                    mHandler.removeCallbacks(mEnterSubfolderRunnable);
                }
            }

            // Scroll the list view
            mList.smoothScrollBy(0, mScrollStatus.getSpeed());

            // Update the list if needed (the hovered item might have changed after
            // scrolling the list)
            // WARNING : smoothScrollBy() is asynchronous so the scrolling is not yet done here!
            updateDragView(mScrollStatus.getDragX(), mScrollStatus.getDragY());

            // Schedule the next update
            mHandler.postDelayed(mScrollListRunnable, LIST_SCROLL_PERIOD);
        }
    };

    private boolean isInsideItemDroppableArea(int x, View view) {
        // Check if the provided position is suitable for the "drop inside folder" feature:
        //  - in single panel mode => the whole item area is allowed.
        //  - in double panel mode => only the left part of the item is allowed.
        return true;
    }

    private boolean itemAcceptsDrop(MetaFile2 file, List<MetaFile2> selectedFiles) {
        // A given item accepts drop if it is a folder and does not belong to the dragged items selection
        boolean isSelected = (selectedFiles != null && selectedFiles.contains(file));
        return (file.isDirectory() && !isSelected);
    }

    private void updateDragView(int x, int y) {
        // Get the position of the item under the current drag point (hovered item)
        View item = mList.findChildViewUnder(x, y);
        int hoveredItemPosition = item != null ? mList.getChildAdapterPosition(item) : -1;

        if (hoveredItemPosition >= 0) {
            // Check if the drag point is inside the area corresponding to the "drop inside folder" feature:
            boolean isInsideHoveredItemDroppableArea = isInsideItemDroppableArea(x, mList);

            // Check if we need to redraw the list
            boolean listNeedsUpdate = false;

            MetaFile2 currentFile = mAdapter.getFile(hoveredItemPosition);
            if (currentFile == null)
                return;
            List<MetaFile2> selectedFiles = getSelectedFiles();

            if (hoveredItemPosition != mHoveredItemPosition) {
                //------------------------------------------------------------------
                // The user is moving its finger over a new item
                //------------------------------------------------------------------
                // Check if the new item should be highlighted
                listNeedsUpdate = (isInsideHoveredItemDroppableArea && itemAcceptsDrop(currentFile, selectedFiles));

                if (!listNeedsUpdate && mHoveredItemPosition >= 0) {
                    // The new item does not need to be redrawn but check if we should remove the highlight from the previous item
                    MetaFile2 previousFile = mAdapter.getFile(mHoveredItemPosition);
                    if (previousFile != null) {
                        listNeedsUpdate = (mIsInsideHoveredItemDroppableArea && itemAcceptsDrop(previousFile, selectedFiles));
                    }
                    else {
                        listNeedsUpdate = false;
                    }
                }
            } else {
                //------------------------------------------------------------------
                // The user is moving its finger inside the same item as previously
                //------------------------------------------------------------------
                // => redraw is needed only if the highlight status of the item has changed
                listNeedsUpdate = (isInsideHoveredItemDroppableArea != mIsInsideHoveredItemDroppableArea && itemAcceptsDrop(currentFile, selectedFiles));
            }

            if (listNeedsUpdate) {
                // Either the new or preview item must be udpated => redraw the list
                mList.invalidate();
            }

            mHoveredItemPosition = hoveredItemPosition;
            mIsInsideHoveredItemDroppableArea = isInsideHoveredItemDroppableArea;
        }
        else {
            // Invalid hovered item
            mHoveredItemPosition = -1;
            mIsInsideHoveredItemDroppableArea = false;
        }
    }

    final private Runnable mEnterSubfolderRunnable = new Runnable() {

        public void run() {
            // Make sure the hovered item is valid
            if (mHoveredItemPosition >= 0) {
                // Make sure the hovered item is a folder
                MetaFile2 file = mAdapter.getFile(mHoveredItemPosition);
                if (file.isDirectory()) {
                    // Store current list to cache
                    mCache.put(mCurrentUri, mAdapter.getFiles(), mLayoutManager.onSaveInstanceState());

                    // Start loading new Uri
                    mUriStack.push(mCurrentUri.toString());
                    mCurrentUri = file.getUri();

                    // Send an asynchronous request to load the contents of the subfolder
                    mBrowsingDirection = BrowsingDirection.FORWARD;
                    startListing(mCurrentUri, 100, file);

                    // Prevent scrolling the list until the subfolder contents is loaded
                    mHandler.removeCallbacks(mScrollListRunnable);
                    mScrollStatus.stop();

                    // Ignore drag move events until the subfolder contents is loaded
                    mDragLocked = true;

                    // Reset the drag variables
                    mInitialDragPosition = -1;
                    mHoveredItemPosition = -1;
                    mPreviousDragEventX = -1;
                    mPreviousDragEventY = -1;
                    mIsInsideHoveredItemDroppableArea = false;
                }
            }
        }
    };

    private boolean filesToPasteIncludeFolders(List<MetaFile2> filesToPaste) {
        for (MetaFile2 fileToPaste : filesToPaste) {
            if (fileToPaste.isDirectory()) {
                return true;
            }
        }
        return false;
    }

    private boolean pasteDirectoryIsDescendantOfSourceDirectories(Uri sourceDirectoryUri, Uri pasteDirectoryUri, List<MetaFile2> filesToPaste) {
        if (pasteDirectoryUri.equals(sourceDirectoryUri)) {
            return true;
        }
        String pasteDirectory = pasteDirectoryUri.toString();
        for (MetaFile2 fileToPaste : filesToPaste) {
            if (fileToPaste.isDirectory()) {
                if (pasteDirectory.contains(fileToPaste.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private int checkPasteStatus(Uri pasteDirectoryUri, List<MetaFile2> filesToPaste) {
        Uri sourceDirectoryUri = Utils.getParentUrl(filesToPaste.get(0).getUri());
        boolean containsFolders = filesToPasteIncludeFolders(filesToPaste);

        // Don't allow to drop a folder into itself or one of its subfolders
        if (containsFolders && pasteDirectoryIsDescendantOfSourceDirectories(sourceDirectoryUri, pasteDirectoryUri, filesToPaste)) {
            return PASTE_ERR_FOLDER;
        }

        return PASTE_OK;
    }

    private void showDropError(int messageId) {
        // Use a toast to display message
        Toast.makeText(getActivity(), getString(messageId), Toast.LENGTH_SHORT).show();

        // Reset the variables before redrawing the list
        mHoveredItemPosition = -1;
        mIsInsideHoveredItemDroppableArea = false;
        mList.invalidate();
    }

    private void dropInDirectory(View view, DragEvent event) {
        final List<MetaFile2> filesToPaste = PasteAndDragBin.getDragbinMetafiles();
        if (filesToPaste.size() <= 0) {
            return;
        }

        // Select the folder where to drop the file (always in the current folder by the way)
        final Uri metaDropDirectoryUri = mCurrentUri;

        // Check if we are allowed to drop the files in the selected folder
        final int pasteStatus = checkPasteStatus(metaDropDirectoryUri, filesToPaste);

        switch (pasteStatus) {
            case PASTE_OK:
                // Pasting is possible here => build a dialog with the available actions
                // so that the user can select which one to perform (files or folders with
                // the same name will be checked when actually performing the action)
                String[] choices = new String[2];
                choices[0] = getResources().getString(android.R.string.copy);
                choices[1] = getResources().getString(R.string.move);
                final ListAdapter adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, choices);

                ListView popupList = new ListView(getActivity());
                popupList.setAdapter(adapter);
                popupList.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

                Rect tmpRect = new Rect();
                int popupWidth = popupList.getPaddingLeft() + popupList.getPaddingRight();
                for (int i = 0; i < adapter.getCount(); i++) {
                    TextView textView = (TextView) adapter.getView(i, null, null).findViewById(android.R.id.text1);
                    String text = textView.getText().toString();
                    textView.getPaint().getTextBounds(text, 0, text.length(), tmpRect);
                    int itemWidth = tmpRect.width() + (tmpRect.left + textView.getPaddingLeft()) * 2;
                    if (itemWidth > popupWidth) {
                        popupWidth = itemWidth;
                    }
                }

                // Make sure the popup is not too small because it does not look good in that case
                if (popupWidth < POPUP_MIN_WIDTH) {
                    popupWidth = POPUP_MIN_WIDTH;
                }

                mPopupValidated = false;
                final PopupWindow popup = new PopupWindow(getActivity(), null, android.R.attr.listPopupWindowStyle);
                popup.setContentView(popupList);
                Drawable popupBackground = popup.getBackground();
                if (popupBackground != null) {
                    popupBackground.getPadding(tmpRect);
                    popupWidth += tmpRect.left + tmpRect.right;
                }
                popup.setWidth(popupWidth);
                popup.setHeight(LayoutParams.WRAP_CONTENT);
                popup.setOutsideTouchable(false);
                popup.setFocusable(true);
                popup.showAtLocation(view, Gravity.NO_GRAVITY, (int)event.getX(), (int)event.getY());

                popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                    public void onDismiss() {
                        if (!mPopupValidated) {
                            // The user cancelled the popup => redraw the list to clear the highlighted item
                            mList.invalidate();
                        }
                    }
                });

                popupList.setOnItemClickListener(new OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        mPopupValidated = true;
                        popup.dismiss();
                        /* TODO DEMO MODE
                        if (ArchosSettings.isDemoModeActive(getActivity())) {
                            getActivity().startService(new Intent(ArchosIntents.ACTION_DEMO_MODE_FEATURE_DISABLED));
                        } else
                        */
                        {
                            if (FileManagerService.fileManagerService != null) {
                                if (position == 0) {
                                    FileManagerService.fileManagerService.copy(filesToPaste, metaDropDirectoryUri);
                                } else if (position == 1) {
                                    FileManagerService.fileManagerService.cut(filesToPaste, metaDropDirectoryUri);
                                }
                            }
                        }
                    }
                });

                break;

            case PASTE_ERR_FOLDER:
                // Trying to paste a folder into itself or one of its subfolders
                showDropError(R.string.cannot_paste_folder_into_itself);
                break;
        }

    }

    @Override
    public void setActive(boolean active, boolean updateUI) {
        super.setActive(active, updateUI);

        if (active) {
            mActionModeManager.updateVisibility();
        } else {
            mActionModeManager.hide();
        }
    }

    public MetaFile2 getCurrentMetaFile() {
        return mCurrentMetaFile;
    }

    /**
     * @param uri is the Uri to load
     * @param delay is used for animation purpose. Must be set to zero if no transition animation is needed
     * @param metaFile2 can be null, is used to retrieve info on current directory
     */
    protected void startListing(final Uri uri, final int delay, final MetaFile2 metaFile2) {
        Log.d(TAG, "startListing " + uri);

        // abort previous engine if there is any
        abortCurrentAndDelayedListing();
        mCurrentMetaFile = metaFile2;
        if (mCurrentMetaFile == null) {
            mMetaFileRetriever.retrieve(uri);
        }
        else if (getActivity() != null) {
            getActivity().invalidateOptionsMenu();
        }

        mCurrentUri = uri;
        mState = State.LOADING;

        // When starting loading we fade-in the progress wheel (for obvious reason) and the progress veil (to hide the previous content)
        mLoadingVeil.animate().alpha(1).setDuration(mListAnimationDuration).setStartDelay(delay); // MAGICAL. Delay to have it not show when the loading is quick
        mLoadingProgress.animate().alpha(1).setDuration(400).setStartDelay(delay); // MAGICAL. Delay to have it not show when the loading is quick

        // List slide
        if (mBrowsingDirection.isKnown()) {
            mList.animate().translationX(mBrowsingDirection.isBackward() ? mListAnimationTranslation : -mListAnimationTranslation)
            .setDuration(mListAnimationDuration).setStartDelay(delay);
        }

        // when delay is null we could run the mListingRunnable right here instead of posting it,
        // but I prefer to limit the number of ways it is working, hence always use posting
        mHandler.postDelayed(mListingRunnable, delay);
    }

    /**
     * @param uri
     * @return true if cached data has been found and loaded, false if not
     */
    private boolean loadCachedList(Uri uri) {
        // Check if we have a cached list for the parent Uri
        final SavedData saved = mCache.get(uri);
        if (saved == null) {
            return false;
        }
        if (saved.mDirty) {
            mRefreshingLayoutState = saved.mListLayoutState;
            mCache.remove(uri);
            return false;
        }

        // abort currently loading engine if there is any
        abortCurrentAndDelayedListing();
        // Load cached data
        mCurrentUri = uri;
        mAdapter.updateData(saved.mFiles);
        mLayoutManager.onRestoreInstanceState(saved.mListLayoutState);
        updateMenu();

        // List slide-in
        mList.setAlpha(0);
        if (mBrowsingDirection.isKnown()) {
            mList.setTranslationX(mBrowsingDirection.isBackward() ? -mListAnimationTranslation : mListAnimationTranslation);
        }
        mList.animate().translationX(0).alpha(1).setDuration(mListAnimationDuration).setStartDelay(100); // MAGICAL little delay to let the adapter populate the list, hum hum...

        // Update UI
        mBreadCrumbTrailerView.setCurrentUri(mCurrentUri, getPreviousUris());
        mEmptyView.setVisibility(View.GONE); // in case we go back from an empty folder ot from an error
        hideVeilAndProgress(true); // better safe than sorry

        // To be consistent we remove the current list from the cache. It will be put again if we navigate forward after
        mCache.remove(uri);

        return true;
    }

    @Override
    public boolean goBackOneLevel() {
        // Can't go back past the root
        if (mCurrentUri.equals(getRootUri())) {
            return false;
        }
        String parentUri = !mUriStack.empty() ? mUriStack.pop() : null;
        Log.d(TAG, "goBackOneLevel() parent = " + parentUri);

        if (parentUri != null) {
            goBackTo(Uri.parse(parentUri));
            return true;
        } else {
            Log.e(TAG, "goBackOneLevel(): Did not manage to get a parent Uri for " + mCurrentUri);
            return false;
        }
    }

    @Override
    public boolean goBackToRoot() {
        // Return false if already at root
        if (mCurrentUri.equals(getRootUri())) {
            return false;
        }
        mUriStack.clear();
        goBackTo(getRootUri());
        return true;
    }

    @Override
    public void notifyServiceCreated() {
        if (FileManagerService.fileManagerService != null) {
            FileManagerService.fileManagerService.addListener(this);
        }
    }

    // BreadCrumbTrailView.OnSegmentClickListener implementation
    @Override
    public void onSegmentClick(Uri segmentUri) {
        // Clear the cached lists from currentUri to segmentUri
        String uri = mCurrentUri.toString();
        while (uri != null && !uri.equals(segmentUri.toString())) { // (uri != null) test is in theory not needed, but better safe than sorry...
            mCache.remove(Uri.parse(uri));
            uri = mUriStack.pop();
        }

        goBackTo(segmentUri);
    }

    /**
     * This method will either get the list from cache (if it is cached) or start loading it
     * @param uri
     */
    private void goBackTo(Uri uri) {
        mCurrentMetaFile = null;
        mMetaFileRetriever.retrieve(uri);
        mBrowsingDirection = BrowsingDirection.BACKWARD;
        // Try to load list from cache, or reload if it fails
        if (!loadCachedList(uri)) {
            startListing(uri, 0, null);
        }
    }

    /**
     * Called each time the content of the list/adapter is modified.
     * It must update the menu if needed in order to comply with the content of the list
     */
    protected void updateMenu() {
        // nothing to do here, but is overridden by child classes
    }

    /**
     * Abort an ongoing engine if there is one + Remove delayed one from the handler queue if there is any
     */
    private void abortCurrentAndDelayedListing() {
        // abort previous engine if there is any
        if (mEngine != null) {
            mEngine.abort();
            mEngine.setListener(null); // not needed in theory but does not harm
            mEngine = null;
        }

        mState = State.IDLE;

        // remove listing runnable that may be delayed
        mHandler.removeCallbacks(mListingRunnable);
    }

    private void hideVeilAndProgress(boolean animated) {
        // Immediately hide the veil
        mLoadingVeil.animate().cancel();
        mLoadingVeil.setAlpha(0);

        if (animated) {
            // Quick fade-out of the progress wheel
            mLoadingProgress.animate().alpha(0).setDuration(mListAnimationDuration);
        } else {
            // Immediately hide
            mLoadingProgress.setAlpha(0);
        }
    }

    @Override
    public void onListingStart() {
        Log.d(TAG, "onListingStart");
        mBreadCrumbTrailerView.setCurrentUri(mCurrentUri, getPreviousUris());
    }

    @Override
    public void onListingUpdate(List<? extends MetaFile2> files) {
        Log.d(TAG, "onListingUpdate");
        for(MetaFile2 metaFile2 : files){
            Log.d(TAG, "file "+metaFile2.getName());

        }
        // cancel pending list animations
        mList.animate().cancel();

        // update list
        mAdapter.updateData(files);
        updateMenu();

        // Check the empty view
        if (files.isEmpty()) {
            mEmptyView.setAlpha(0);
            mEmptyView.setVisibility(View.VISIBLE);
            mEmptyTextView.setText(R.string.directory_empty);
            mEmptyTextView.setTextColor(getResources().getColor(R.color.empty_view_text_color));
            // Quick fade-in
            mEmptyView.animate().alpha(1).setDuration(mListAnimationDuration);
        }
        else {
            mEmptyView.setVisibility(View.GONE);
        }

        hideVeilAndProgress(true);

        mList.setAlpha(0);
        if (mBrowsingDirection.isKnown()) {
            mList.setTranslationX(mBrowsingDirection.isBackward() ? -mListAnimationTranslation : mListAnimationTranslation);
        }
        mList.animate().translationX(0).alpha(1).setDuration(mListAnimationDuration).setStartDelay(100); // MAGICAL little delay to let the adapter populate the list, hum hum...

        // Restore list position in case of refresh
        if (mRefreshingLayoutState != null) {
            mLayoutManager.onRestoreInstanceState(mRefreshingLayoutState);
        } else {
            mList.scrollToPosition(0); // reset position to top when loading a new view
        }
    }

    public void refresh() {
        mRefreshingLayoutState = mLayoutManager.onSaveInstanceState();
        mBrowsingDirection = BrowsingDirection.UNKNOWN;
        startListing(mCurrentUri, 0, mCurrentMetaFile);
    }

    private void askToRefreshParent() {
        if (!mCurrentUri.equals(getRootUri()) && !mUriStack.isEmpty()) {
            String parentUri = mUriStack.peek();
            if (parentUri != null) {
                mCache.setDirty(Uri.parse(parentUri));
            }
        }
    }

    private void refreshIfNeeded(Uri uriToRefresh, boolean parentNeedToBeRefreshed) {
        if (mCurrentUri == null) {
            return;
        }
        if (parentNeedToBeRefreshed == true) {
            if (mCurrentUri.equals(uriToRefresh)) {
                refresh();
                askToRefreshParent();
            }
            else if (!uriToRefresh.equals(getRootUri()) && mCurrentUri.equals(getParentUri(uriToRefresh))) {
                refresh();
            }
        } else {
            if (mCurrentUri.equals(uriToRefresh)) {
                refresh();
            }
        }
    }

    private void refreshAfterFileOps() {
        FileManagerService fms = FileManagerService.fileManagerService;
        if (fms != null) {
            if (fms.isCopyAction() || fms.isExtractionAction()) {
                refreshIfNeeded(fms.getTarget(), true);
            }
            else if (fms.isCompressionAction()) {
                refreshIfNeeded(getParentUri(fms.getTarget()), true);
            }
            else if (fms.isCutAction()) {
                refreshIfNeeded(getParentUri(fms.getSource()), true);
                refreshIfNeeded(fms.getTarget(), true);
            }
            else if (fms.isDeleteAction()) {
                refreshIfNeeded(getParentUri(fms.getSource()), true);
            }
        }
    }

    protected void showEmptyView(String message, int textColor, boolean showRetryButton) {
        hideVeilAndProgress(true);
        mRefreshingLayoutState = null;
        mEmptyView.setAlpha(0);
        mEmptyView.setVisibility(View.VISIBLE);
        mEmptyTextView.setText(message);
        mEmptyTextView.setTextColor(textColor);
        mRetryButton.setVisibility(showRetryButton ? View.VISIBLE : View.GONE);
        // Quick fade-in
        mEmptyView.animate().alpha(1).setDuration(mListAnimationDuration);
    }

    @Override
    public void onListingEnd() {
        Log.d(TAG, "onListingEnd");
        mState = State.IDLE;
        mBrowsingDirection = BrowsingDirection.UNKNOWN; // reset
        mRefreshingLayoutState = null;
        mDragLocked = false;
        // maybe already done by onListingUpdate(), but also needed here in case of error, because onListingUpdate() is not called in that case
        hideVeilAndProgress(true);
    }

    @Override
    public void onListingFatalError(Exception e, ErrorEnum error) {
        Log.d(TAG, "onListingFatalError", e);

        String message;
        if (e != null) {
            message = e.getLocalizedMessage();
        } else {
            message = getString(R.string.error_listing);
        }
        // Errors are displayed in the empty view
        showEmptyView(message,getResources().getColor(R.color.error_view_text_color),false);
    }

    @Override
    public void onCredentialRequired(Exception e) {
        Log.d(TAG, "onCredentialRequired", e);
        Toast.makeText(getActivity(), " onCredentialRequired", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onListingTimeOut() {
        showEmptyView(getString(R.string.error_time_out), getResources().getColor(R.color.error_view_text_color), true);
    }

    public List<MetaFile2> getSelectedFiles() {
        return (mAdapter != null) ? mAdapter.getSelectedFiles() : null;
    }

    public Uri getCurrentUri() {
        return mCurrentUri;
    }

    @Override
    public void onActionStart() {
        // no need to be implemented here
    }

    @Override
    public void onActionStop() {
        refreshAfterFileOps();
    }

    @Override
    public void onActionError() {
        refreshAfterFileOps();
    }

    @Override
    public void onActionCanceled() {
        refreshAfterFileOps();
    }

    @Override
    public void onProgressUpdate() {
        // no need to be implemented here
    }

}
