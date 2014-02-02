package org.andstatus.app;

import android.app.Activity;

/**
 * Activity should implement this interface in order to use {@link MyActionBar} 
 * @author yvolk@yurivolkov.com
 */
public interface MyActionBarContainer {
    Activity getActivity();
    void closeAndGoBack();
}
