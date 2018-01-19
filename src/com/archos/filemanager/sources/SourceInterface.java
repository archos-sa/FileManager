package com.archos.filemanager.sources;

/**
 * Simple interface between RootActivity and SourceFragment for navigation among the root sources: local, DScard, network, USB
 * @author vapillon
 *
 */
public interface SourceInterface {

    /**
     * User pressed on Network
     */
    void onNetworkSelected(boolean hovered);

    /**
     * User pressed on any source button except network
     */
    void onSourceButtonSelected(SourceInfo info,boolean hovered);

    /**
     * User started to drag files
     */
    void onDragStarted();

    /**
     * Warn interface that a source has disappear
     * @param unmountedSource
     * @param fallbackSource
     */
    void onSourceUnmounted(SourceInfo unmountedSource, SourceInfo fallbackSource);

}
