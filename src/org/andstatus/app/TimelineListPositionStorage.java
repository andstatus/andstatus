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

package org.andstatus.app;

import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.andstatus.app.TimelineActivity.TimelineTitle;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;

/**
 * Determines where to save / retrieve position in the list
 * Two rows are always stored for each position hence two keys. 
 * Plus Query string is being stored for the search results.
 * 2014-10-19 We are storing {@link MyDatabase.Msg#SENT_DATE} for the last retrieved item, not its ID as before
 * @author yvolk@yurivolkov.com
 */
class TimelineListPositionStorage {
    private static final String KEY_PREFIX = "last_position_";

    private CursorAdapter mAdapter;
    private ListView mListView;
    private TimelineListParameters mListParameters;    
    
    /**
     * MyAccount for SharedPreferences ( =="" for DefaultSharedPreferences) 
     */
    String accountGuid = "";
    /**
     * SharePreferences to use for storage 
     */
    private SharedPreferences sp = null;
    /**
     * Key name for the first visible item
     */
    private String keyFirst = "";
    /**
     * Key for the last item we should retrieve before restoring position
     */
    private String keyLast = "";
    private String keyQueryString = "";
    private String queryString;
    
    TimelineListPositionStorage(ListAdapter listAdapter, ListView listView, TimelineListParameters listParameters) {
        this.mAdapter = (CursorAdapter) listAdapter;
        this.mListView = listView;
        this.mListParameters = listParameters;
        
        queryString = listParameters.mSearchQuery; 
        if ((listParameters.mTimelineType != TimelineTypeEnum.USER) && !listParameters.mTimelineCombined) {
            MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(listParameters.myAccountUserId);
            if (ma != null) {
                sp = ma.getAccountPreferences();
                accountGuid = ma.getAccountName();
            } else {
                MyLog.e(this, "No account for IserId=" + listParameters.myAccountUserId);
            }
        }
        if (sp == null) {
            sp = MyPreferences.getDefaultSharedPreferences();
        }
        
        keyFirst = KEY_PREFIX
                + listParameters.mTimelineType.save()
                + (listParameters.mTimelineType == TimelineTypeEnum.USER ? "_user"
                        + Long.toString(listParameters.mSelectedUserId) : "") + (TextUtils.isEmpty(queryString) ? "" : "_search");
        keyLast = keyFirst + "_last";
        keyQueryString = KEY_PREFIX + listParameters.mTimelineType.save() + "_querystring";
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
        long lastItemId = 0;
        if (firstVisiblePosition >= 0) {
            firstVisibleItemId = la.getItemId(firstVisiblePosition);
            MyLog.v(this, method + " firstVisiblePos:" + firstVisiblePosition + " of " + itemCount
                    + "; itemId:" + firstVisibleItemId);
            // We will load one more "page of messages" below (older) current top item
            lastPosition = firstVisiblePosition + TimelineListParameters.PAGE_SIZE;
            if (lastPosition >= itemCount) {
                lastPosition = itemCount - 1;
            }
            if (lastPosition >= TimelineListParameters.PAGE_SIZE) {
                lastItemId = la.getItemId(lastPosition);
            }
        }

        if (firstVisibleItemId <= 0) {
            MyLog.v(this, method + " failed: no visible items for " + new TimelineTitle(mListParameters, "").toString());
            return false;
        } else {
            put(firstVisibleItemId, lastItemId);

            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + " succeeded \"" + accountGuid + "\"; " + keyFirst + "="
                        + firstVisibleItemId + ", pos=" + firstVisiblePosition + "; lastId="
                        + lastItemId + ", pos=" + lastPosition);
            }
        }
        return true;
    }
    
    void put(long firstVisibleItemId, long lastRetrievedSentDate) {
        sp.edit().putLong(keyFirst, firstVisibleItemId)
        .putLong(keyLast, lastRetrievedSentDate)
        .putString(keyQueryString, queryString).commit();
    }

    private static final long NOT_FOUND_IN_LIST_POSITION_STORAGE = -4;
    private static final long NOT_FOUND_IN_SHARED_PREFERENCES = -1;
    /** Valid data position value is > 0 */
    long getFirstVisibleSentDate() {
        long savedItemId = NOT_FOUND_IN_LIST_POSITION_STORAGE;
        if (isThisPositionStored()) {
            savedItemId = sp.getLong(keyFirst, NOT_FOUND_IN_SHARED_PREFERENCES);
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
            savedItemId = sp.getLong(keyLast, NOT_FOUND_IN_SHARED_PREFERENCES);
        }
        return savedItemId;
    }
    
    void clear() {
        sp.edit().remove(keyFirst).remove(keyLast)
                .remove(keyQueryString).commit();
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "Position forgot   \"" + accountGuid + "\"; " + keyFirst);
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
            firstItemId = getFirstVisibleSentDate();
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
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + " succeeded \"" + accountGuid + "\"; " + keyFirst + "="
                        + firstItemId +"; index=" + scrollPos);
            }
        } else {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, method + " failed \"" + accountGuid + "\"; " + keyFirst + "="
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
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "setSelectionAtBottom, 1");
        }
        int viewHeight = mListView.getHeight();
        int childHeight;
        childHeight = 30;
        int y = viewHeight - childHeight;
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
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
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
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