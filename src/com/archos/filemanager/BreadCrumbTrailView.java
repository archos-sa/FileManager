package com.archos.filemanager;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import com.archos.filecorelibrary.Utils;
import com.archos.filemanager.listing.ListingFragment;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

public class BreadCrumbTrailView extends FrameLayout {

    private static final String TAG = "BreadCrumbTrailView";
    private static final boolean DBG = false;

    private static int GO_BACK_TO_PARENT_DELAY = 1000; // delay in ms before go back to hovered segment

    public interface OnSegmentClickListener {
        void onSegmentClick(Uri segmentUri);
    }

    private LayoutInflater mLI;
    private Uri mRootUri = null;;
    private String mRootName = "";
    private Uri mCurrentUri = null;

    private HorizontalScrollView mScrollView;
    private ViewGroup mContainer;

    private List<OnSegmentClickListener> mListeners = new LinkedList<OnSegmentClickListener>();

    private View mHoveredSegment;
    private float mPreviousDragEventX;
    private float mPreviousDragEventY;

    final private Handler mHandler = new Handler();


    public BreadCrumbTrailView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }
    public BreadCrumbTrailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mLI = LayoutInflater.from(context);
        mLI.inflate(R.layout.breadcrumbtrail_view, this, true);
        mScrollView = (HorizontalScrollView)findViewById(R.id.scrollview);
        mContainer = (ViewGroup)findViewById(R.id.container);
        mContainer.setOnDragListener(mSegmentOnDragListener);
    }

    final Runnable mFullScrollRightRunnable = new Runnable() {
        public void run() {
            mScrollView.fullScroll(View.FOCUS_RIGHT);
        }
    };

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            // Scroll to the right must be done asynchronously so that it is done after the actual layout phase
            this.post(mFullScrollRightRunnable);
        }
    }

    public void addOnSegmentClickListener(OnSegmentClickListener listener) {
        mListeners.add(listener);
    }

    public void removeOnSegmentClickListener(OnSegmentClickListener listener) {
        mListeners.remove(listener);
    }

    /**
     * @param rootUri the starting level
     * @param rootName The displayed name for the root
     */
    public void setRoot(Uri rootUri, String rootName) {
        if(DBG) Log.d(TAG, "setRoot "+rootUri);
        mRootUri = rootUri;
        mRootName = rootName;
    }

    public void setCurrentUri(Uri uri, List<Uri> previousUris) {
        if(DBG) Log.d(TAG, "setCurrentUri "+uri);

        if (mRootUri==null) {
            throw new IllegalStateException("Root must be set using setRoot() before calling (setCurrentUri)");
        }

        mCurrentUri = uri;

        // Keep it simple, start from a clean sheet! (can be optimized later...)
        mContainer.removeAllViews();

        // Build the list of path from CurrentUri to root
        List<Uri> uriList = new LinkedList<Uri>();
        uriList.addAll(previousUris);
        uriList.add(mCurrentUri);


        boolean first=true;
        for(Uri u : uriList) {
            if (!first) {
                addSeparator();
            }
            first=false;
            if (u.equals(mRootUri)) {
                addSegment(u, mRootName);
            } else {
                addSegment(u, u.getLastPathSegment());
            }
        }
        // Scroll to the right must be done asynchronously so that it is done after the actual layout phase
        this.post(mFullScrollRightRunnable);
    }

    private void addSegment(Uri uri, String name) {
        TextView segment = (TextView)mLI.inflate(R.layout.main_action_bar_item, mContainer, false);
        segment.setText(name);
        segment.setTag(uri);
        segment.setEnabled(!uri.equals(mCurrentUri));
        segment.setOnClickListener(mSegmentOnClickListener);
        mContainer.addView(segment, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
    }

    private void addSeparator() {
        mLI.inflate(R.layout.main_action_bar_separator, mContainer, true);
    }

    private final OnClickListener mSegmentOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Uri uri = (Uri)v.getTag();
            for (OnSegmentClickListener l : mListeners) {
                l.onSegmentClick(uri);
            }
        }
    };

    private final OnDragListener mSegmentOnDragListener = new OnDragListener() {
        public boolean onDrag(View view, DragEvent event) {
            boolean dragEventHandled = false;

            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_STARTED");
                    mHoveredSegment = null;
                    dragEventHandled = true;
                    break;

                case DragEvent.ACTION_DRAG_ENTERED:
                    if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_ENTERED");
                    mPreviousDragEventX = (int)event.getX();
                    mPreviousDragEventY = (int)event.getY();
                    break;

                case DragEvent.ACTION_DRAG_LOCATION:
                    if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_LOCATION");
                    final float x = event.getX();
                    final float y = event.getY();

                    // Check which segment is currently below the finger (hovered segment)
                    final View hoveredSegment = findSegmentUnder((ViewGroup)view, x, y);

                    // Update the display if needed
                    if (hoveredSegment != null && !hoveredSegment.equals(mHoveredSegment)) {
                        // Highlight the hovered segment
                        changeSegmentBackground(hoveredSegment, true);

                        // Remove the highlight on the previous hovered segment
                        changeSegmentBackground(mHoveredSegment, false);

                        mHoveredSegment = hoveredSegment;
                        mHandler.removeCallbacks(mGoBackToParentRunnable);
                        mHandler.postDelayed(mGoBackToParentRunnable, GO_BACK_TO_PARENT_DELAY);
                    }

                    // Some touch-screens keep sending ACTION_DRAG_LOCATION events repeatedly
                    // even if the finger doesn't move => ignore these events if the drag point doesn't 
                    // move (or only by a very small amount to filter the touchscreen erratic response)
                    if (Math.abs(x - mPreviousDragEventX) > ListingFragment.VALID_MOVE_EVENT_THRESHOLD || Math.abs(y - mPreviousDragEventY) > ListingFragment.VALID_MOVE_EVENT_THRESHOLD) {
                        if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_LOCATION at x=" + x + " y=" + y);
                        mHandler.removeCallbacks(mGoBackToParentRunnable);

                        // Allow an action if the hovered segment is valid
                        if (hoveredSegment != null) {
                            mHandler.postDelayed(mGoBackToParentRunnable, GO_BACK_TO_PARENT_DELAY);
                        }
                        mPreviousDragEventX = x;
                        mPreviousDragEventY = y;
                    }
                    break;

                case DragEvent.ACTION_DRAG_EXITED:
                    if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_EXITED");
                    mHandler.removeCallbacks(mGoBackToParentRunnable);
                    changeSegmentBackground(mHoveredSegment, false);
                    mHoveredSegment = null;
                    break;

                case DragEvent.ACTION_DRAG_ENDED:
                    if(DBG) Log.d(TAG, "onDrag : ACTION_DRAG_ENDED");
                    mHandler.removeCallbacks(mGoBackToParentRunnable);
                    changeSegmentBackground(mHoveredSegment, false);
                    break;
            }

            return dragEventHandled;
        }
    };

    private View findSegmentUnder(ViewGroup parent, float x, float y) {
        for (int i=0;  i<parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView) {
                if (x >= child.getLeft() && x <= child.getRight() && y >= child.getTop() && y <= child.getBottom()) {
                    return child.isEnabled() ? child : null;
                }
            }
        }
        return null;
    }

    private void changeSegmentBackground(View segment, boolean highlighted) {
        if (segment != null) {
            // need to save the padding and reapply it because changing the background resource seems to reset the padding...
            final int paddingLeft = segment.getPaddingLeft();
            final int paddingRight = segment.getPaddingRight();
            final int paddingTop = segment.getPaddingTop();
            final int paddingBottom = segment.getPaddingBottom();
            segment.setBackgroundResource(highlighted ? R.drawable.main_action_bar_item_background_hovered : R.drawable.main_action_bar_item_background);
            segment.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        }
    }

    final private Runnable mGoBackToParentRunnable = new Runnable() {
        public void run() {
            // Make sure the hovered segment is valid
            if (mHoveredSegment != null) {
                Uri uri = (Uri)mHoveredSegment.getTag();
                for (OnSegmentClickListener l : mListeners) {
                    l.onSegmentClick(uri);
                }
            }
        }
    };

}
