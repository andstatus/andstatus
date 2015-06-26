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
import android.database.Cursor;
import android.text.TextUtils;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.msg.TimelineActivity.TimelineTitle;
import org.andstatus.app.util.MyLog;

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

    private CursorAdapter mAdapter;
    private ListView mListView;
    private TimelineListParameters mListParameters;    
    
    private long mUserId = 0;
    /**
     * SharePreferences to use for storage 
     */
    private SharedPreferences sp = MyPreferences.getDefaultSharedPreferences();
    private String keyFirstVisibleItemId = "";
    private String keyLastRetrievedItemSentDate = "";
    private String keyQueryString = "";
    private String queryString;
    
    TimelineListPositionStorage(ListAdapter listAdapter, ListView listView, TimelineListParameters listParameters) {
        this.mAdapter = (CursorAdapter) listAdapter;
        this.mListView = listView;
        this.mListParameters = listParameters;
        
        queryString = listParameters.mSearchQuery; 
        if (listParameters.mTimelineType == TimelineType.USER) {
            mUserId = listParameters.mSelectedUserId;
        } else if (!listParameters.mTimelineCombined) {
            mUserId = listParameters.myAccountUserId;
        }
        keyFirstVisibleItemId = KEY_PREFIX
                + listParameters.mTimelineType.save()
                + "_user" + Long.toString(mUserId)
                + (TextUtils.isEmpty(queryString) ? "" : "_search");
        keyLastRetrievedItemSentDate = keyFirstVisibleItemId + "_last";
        keyQueryString = keyFirstVisibleItemId + "_querystring";
    }

    /**  @return true for success */
    boolean save() {
        if (mListView == null) {
            return false;
        }
        final String method = "saveListPosition";
        CursorAdapter la = mAdapter;
        if (la == null) {
            MyLog.v(this, method + " skipped: no ListAdapter");
            return false;
        }
        if (mListParameters.isEmpty()) {
            MyLog.v(this, method + " skipped: no listParameters");
            return false;
        }
        Cursor cursor = la.getCursor(); 
        if (la.getCursor() == null) {
            MyLog.v(this, method + " skipped: cursor is null");
            return false;
        }
        if (la.getCursor() == null || la.getCursor().isClosed()) {
            MyLog.v(this, method + " skipped: cursor is closed");
            return false;
        }

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
            if (cursor.moveToPosition(lastPosition)) {
                lastItemSentDate = cursor.getLong(cursor.getColumnIndex(MyDatabase.Msg.SENT_DATE));
            }
        }

        if (firstVisibleItemId <= 0) {
            MyLog.v(this, method + " failed: no visible items for " + new TimelineTitle(mListParameters, "").toString());
            return false;
        } else {
            put(firstVisibleItemId, lastItemSentDate);

            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, method + " succeeded key=" + keyFirstVisibleItemId + ", id="
                        + firstVisibleItemId + ", pos=" + firstVisiblePosition + ", lastdate="
                        + new Date(lastItemSentDate).toString() + ", lastpos=" + lastPosition);
            }
        }
        return true;
    }
    
    void put(long firstVisibleItemId, long lastRetrievedSentDate) {
        sp.edit().putLong(keyFirstVisibleItemId, firstVisibleItemId)
        .putLong(keyLastRetrievedItemSentDate, lastRetrievedSentDate)
        .putString(keyQueryString, queryString).commit();
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

    /** Valid data position value is > 0 */
    long getLastRetrievedSentDate() {
        long savedItemId = NOT_FOUND_IN_LIST_POSITION_STORAGE;
        if (isThisPositionStored()) {
            savedItemId = sp.getLong(keyLastRetrievedItemSentDate, NOT_FOUND_IN_SHARED_PREFERENCES);
        }
        return savedItemId;
    }
    
    void clear() {
        sp.edit().remove(keyFirstVisibleItemId).remove(keyLastRetrievedItemSentDate)
                .remove(keyQueryString).commit();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "Position forgot key=" + keyFirstVisibleItemId);
        }
    }
    
    /**
     * Restore (First visible item) position saved for this user and for this type of timeline
     */
    public boolean restoreListPosition() {
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
                if (TextUtils.isEmpty(mListParameters.mSearchQuery)) {
                    scrollPos = mListView.getCount() - 2;
                } else {
                    // In search mode start from the most recent message!
                    scrollPos = 0;
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
        for (listPos = 0; !itemFound && (listPos < itemCount); listPos++) {
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