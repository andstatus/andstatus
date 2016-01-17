/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.msg;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.ListView;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.msg.TimelineActivity.TimelineTitle;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.widget.MyBaseAdapter;

import java.util.Date;

/**
 * Determines where to save / retrieve position in the list
 * Information on two rows is stored for each "position" hence two keys. 
 * Plus Query string is being stored for the search results.
 * 2014-11-15 We are storing {@link MyDatabase.Msg#SENT_DATE} for the last item to retrieve, not its ID as before
 * @author yvolk@yurivolkov.com
 */
class TimelineListPositionStorage {
    private static final String KEY_PREFIX = "timeline_position_";

    private final TimelineAdapter mAdapter;
    private final ListView mListView;
    private final TimelineListParameters mListParameters;

    /**
     * SharePreferences to use for storage 
     */
    private final SharedPreferences sp = MyPreferences.getDefaultSharedPreferences();
    private String keyFirstVisibleItemId = "";
    private String keyLastRetrievedItemSentDate = "";
    private String keyQueryString = "";
    private final String queryString;
    
    TimelineListPositionStorage(TimelineAdapter listAdapter, ListView listView, TimelineListParameters listParameters) {
        this.mAdapter = listAdapter;
        this.mListView = listView;
        this.mListParameters = listParameters;
        
        queryString = listParameters.mSearchQuery;
        long userId = 0;
        if (listParameters.mTimelineType == TimelineType.USER) {
            userId = listParameters.mSelectedUserId;
        } else if (!listParameters.mTimelineCombined) {
            userId = listParameters.myAccountUserId;
        }
        keyFirstVisibleItemId = KEY_PREFIX
                + listParameters.mTimelineType.save()
                + "_user" + Long.toString(userId)
                + (TextUtils.isEmpty(queryString) ? "" : "_search");
        keyLastRetrievedItemSentDate = keyFirstVisibleItemId + "_last";
        keyQueryString = keyFirstVisibleItemId + "_query_string";
    }

    void save() {
        final String method = "saveListPosition";
        if (mListView == null || mAdapter == null
                || mListParameters == null || mListParameters.isEmpty()) {
            MyLog.v(this, method + ": skipped");
            return;
        }
        TimelineAdapter la = mAdapter;
        int firstVisiblePosition = mListView.getFirstVisiblePosition();
        // Don't count a footer
        int itemCount = la.getCount() - 1;
        if (firstVisiblePosition >= itemCount) {
            firstVisiblePosition = itemCount - 1;
        }
        long firstVisibleItemId = 0;
        int lastPosition = -1;
        long lastItemSentDate = System.currentTimeMillis();
        if (firstVisiblePosition >= 0) {
            firstVisibleItemId = la.getItemId(firstVisiblePosition);
            MyLog.v(this, method + " firstVisiblePos:" + firstVisiblePosition + " of " + itemCount
                    + "; itemId:" + firstVisibleItemId);
            // We will load one more "page of messages" below (older) current top item
            lastPosition = firstVisiblePosition + TimelineListParameters.PAGE_SIZE;
            if (lastPosition >= itemCount) {
                lastPosition = itemCount - 1;
            }
            lastItemSentDate = la.getItem(lastPosition).sentDate;
        }

        if (firstVisibleItemId <= 0) {
            MyLog.v(this, method + " failed: no visible items for " + new TimelineTitle(mListParameters, "").toString());
        } else {
            put(firstVisibleItemId, lastItemSentDate);

            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + " succeeded key=" + keyFirstVisibleItemId + ", id="
                        + firstVisibleItemId + ", pos=" + firstVisiblePosition + ", lastDate="
                        + new Date(lastItemSentDate).toString() + ", lastPos=" + lastPosition);
            }
        }
    }
    
    void put(long firstVisibleItemId, long lastRetrievedSentDate) {
        sp.edit().putLong(keyFirstVisibleItemId, firstVisibleItemId)
        .putLong(keyLastRetrievedItemSentDate, lastRetrievedSentDate)
        .putString(keyQueryString, queryString).apply();
    }

    private static final long NOT_FOUND_IN_LIST_POSITION_STORAGE = -4;
    private static final long NOT_FOUND_IN_SHARED_PREFERENCES = -1;
    /** Valid data position value is > 0 */
    long getFirstVisibleItemId() {
        long savedItemId = NOT_FOUND_IN_LIST_POSITION_STORAGE;
        if (isThisPositionStored()) {
            savedItemId = sp.getLong(keyFirstVisibleItemId, NOT_FOUND_IN_SHARED_PREFERENCES);
        }
        return savedItemId;
    }
    
    private boolean isThisPositionStored() {
        return queryString.compareTo(sp.getString(
                        keyQueryString, "")) == 0;
    }

    /** @return 0 if not found */
    long getLastRetrievedSentDate() {
        long date = 0;
        if (isThisPositionStored()) {
            date = sp.getLong(keyLastRetrievedItemSentDate, 0);
        }
        return date;
    }
    
    void clear() {
        sp.edit().remove(keyFirstVisibleItemId).remove(keyLastRetrievedItemSentDate)
                .remove(keyQueryString).apply();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "Position forgot key=" + keyFirstVisibleItemId);
        }
    }
    
    /**
     * Restore (First visible item) position saved for this user and for this type of timeline
     */
    public boolean restore() {
        if (mListView == null) {
            return false;
        }
        final String method = "restoreListPosition";
        boolean loaded = false;
        int scrollPos = -1;
        long firstItemId = -3;
        try {
            firstItemId = getFirstVisibleItemId();
            if (firstItemId > 0) {
                scrollPos = listPosForId(firstItemId);
            }
            if (scrollPos >= 0) {
                mListView.setSelectionFromTop(scrollPos, 0);
                loaded = true;
            } else {
                // There is no stored position
                if (mListParameters.whichPage == WhichTimelinePage.YOUNGEST
                        || !TextUtils.isEmpty(mListParameters.mSearchQuery)
                        || mListView.getCount() > 2) {
                    // In search mode start from the most recent message!
                    scrollPos = 0;
                } else {
                    scrollPos = mListView.getCount() - 2;
                }
                if (scrollPos >= 0) {
                    setSelectionAtBottom(scrollPos);
                }
            }
        } catch (Exception e) {
            MyLog.v(this, method, e);
        }
        if (loaded) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + " succeeded key=" + keyFirstVisibleItemId + ", id="
                        + firstItemId +"; index=" + scrollPos);
            }
        } else {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + " failed key=" + keyFirstVisibleItemId + ", id="
                        + firstItemId);
            }
            clear();
        }
        return true;
    }

    private void setSelectionAtBottom(int scrollPos) {
        if (mListView == null) {
            return;
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "setSelectionAtBottom, 1");
        }
        int viewHeight = mListView.getHeight();
        int childHeight;
        childHeight = 30;
        int y = viewHeight - childHeight;
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "set position of last item to " + y + "px");
        }
        mListView.setSelectionFromTop(scrollPos, y);
    }

    /**
     * Returns the position of the item with the given ID.
     * 
     * @param searchedId the ID of the item whose position in the list is to be
     *            returned.
     * @return the position in the list or -1 if the item was not found
     */
    private int listPosForId(long searchedId) {
        if (mListView == null) {
            return -1;
        }
        int listPos;
        boolean itemFound = false;
        int itemCount = mListView.getCount();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "item count: " + itemCount);
        }
        for (listPos = 0; listPos < itemCount; listPos++) {
            long itemId = mListView.getItemIdAtPosition(listPos);
            if (itemId == searchedId) {
                itemFound = true;
                break;
            }
        }

        if (!itemFound) {
            listPos = -1;
        }
        return listPos;
    }
}