package com.archos.filemanager.listing;

enum BrowsingDirection {
    UNKNOWN,
    FORWARD, 
    BACKWARD;
    
    public boolean isKnown() {
        return (this!=UNKNOWN);
    }
    public boolean isForward() {
        return (this==FORWARD);
    }
    public boolean isBackward() {
        return (this==BACKWARD);
    }
}