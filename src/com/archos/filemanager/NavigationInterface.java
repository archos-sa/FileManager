package com.archos.filemanager;

/**
 * Simple interface used by the BasePanelFragment instances to request some kind of navigation to RootActivity
 * @author vapillon
 *
 */
public interface NavigationInterface {

    void focusRequest(BasePanelFragment fragmentRequestingFocus);
}
