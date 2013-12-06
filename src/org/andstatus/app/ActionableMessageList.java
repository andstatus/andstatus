package org.andstatus.app;

import android.app.Activity;

import org.andstatus.app.data.TimelineTypeEnum;

/**
 * Activity should implement this interface in order to use {@link MessageContextMenu} 
 * @author yvolk@yurivolkov.com
 */
public interface ActionableMessageList {
    Activity getActivity();
    MessageEditor getMessageEditor();
    long getLinkedUserIdFromCursor(int position);
    long getCurrentMyAccountUserId();
    long getSelectedUserId();
    TimelineTypeEnum getTimelineType();
    boolean isTimelineCombined();
}
