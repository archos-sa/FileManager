package com.archos.filemanager.listing;

import android.util.Log;


public final class ListingScrollStatus {
    private static final String TAG = "ListingScrollStatus";
    private static final boolean DBG = false;

    private static int LISTING_SCROLL_AREA_HEIGHT_PERCENT = 20;    // height of the top and bottom areas used to trigger scrolling (percentage of the Listing height)

    private int dragX;              // the current x-coordinate of the drag point
    private int dragY;              // the current y-coordinate of the drag point
    private int height;             // the height of the Listing
    private int scrollAreaHeight;   // the height of the top and bottom areas used to enable scrolling
    private int speed;              // the current scrolling speed (>0 when scrolling down, <0 when scrolling up)
    private int maxSpeed;
    private boolean isScrolling;

    public ListingScrollStatus() {
       // Scrolling is off at start
       reset();
    }

    public void reset() {
       if (DBG) Log.d(TAG, "reset");
       dragX = 0;
       dragY = 0;
       height = 0;
       scrollAreaHeight = 0;
       isScrolling = false;
       speed = 0;
    }

    public void init(int height, int maxSpeed) {
       if (DBG) Log.d(TAG, "init " + height);
       this.height = height;
       this.scrollAreaHeight = height * LISTING_SCROLL_AREA_HEIGHT_PERCENT / 100;
       this.maxSpeed = maxSpeed;
    }

    public void start() {
        if (DBG) Log.d(TAG, "start");
        isScrolling = true;
    }

    public void stop() {
        if (DBG) Log.d(TAG, "stop");
        isScrolling = false;
    }

    public boolean isScrolling() {
        return isScrolling;
    }

    public void computeSpeedFromDragPosition(int x, int y, boolean scrollable) {
        dragX = x;
        dragY = y;

        if (!scrollable) {
            speed = 0;
            return;
        }

        // There has been one crash report on Google Play due to scrollAreaHeight==0

        if (y<scrollAreaHeight && scrollAreaHeight!=0) {
            // Top area => the closer to the top edge, the faster is the scrolling
            speed = (y - scrollAreaHeight) * maxSpeed / scrollAreaHeight;
        }
        else if (y>height-scrollAreaHeight && scrollAreaHeight!=0) {
            // Bottom area => the closer to the bottom edge, the faster is the scrolling
            speed = (y - height + scrollAreaHeight) * maxSpeed / scrollAreaHeight;
        }
        else {
            // Middle area => don't scroll
            speed = 0;
        }
    }

    public int getSpeed() {
        return speed;
    }

    public int getDragX() {
        return dragX;
    }

    public int getDragY() {
        return dragY;
    }
}
