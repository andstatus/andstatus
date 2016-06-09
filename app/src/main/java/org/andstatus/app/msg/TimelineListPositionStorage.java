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

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * Determines where to save / retrieve position in the list
 * Information on two rows is stored for each "position" hence two keys. 
 * Plus Query string is being stored for the search results.
 * 2014-11-15 We are storing {@link MsgTable#SENT_DATE} for the last item to retrieve, not its ID as before
 * @author yvolk@yurivolkov.com
 */
class TimelineListPositionStorage {
    public static final String TAG = TimelineListPositionStorage.class.getSimpleName();
    private static final String KEY_PREFIX = "timeline_position_";
    private static final int NOT_STORED = -1;

    private final TimelineAdapter mAdapter;
    private final ListView mListView;
    private final TimelineListParameters mListParameters;

    /**
     * SharePreferences to use for storage 
     */
    private final SharedPreferences sp = SharedPreferencesUtil.getDefaultSharedPreferences();
    private String keyFirstVisibleItemId = "";
    private String keyFirstVisibleItemY = "";
    private String keyMinSentDate = "";
    private String keyQueryString = "";
    private final String queryString;

    static class TLPosition {
        long firstVisibleItemId = NOT_STORED;
        long minSentDate = NOT_STORED;
        int y = NOT_STORED;
        String queryString = "";
    }

    TimelineListPositionStorage(TimelineAdapter listAdapter, ListView listView, TimelineListParameters listParameters) {
        this.mAdapter = listAdapter;
        this.mListView = listView;
        this.mListParameters = listParameters;
        
        queryString = listParameters.getTimeline().getSearchQuery();
        long userId = 0;
        if (listParameters.getTimelineType() == TimelineType.USER) {
            userId = listParameters.getSelectedUserId();
        } else if (!listParameters.mTimelineCombined) {
            userId = listParameters.getMyAccount().getUserId();
        }
        keyFirstVisibleItemId = KEY_PREFIX
                + listParameters.getTimelineType().save()
                + "_user" + Long.toString(userId)
                + (TextUtils.isEmpty(queryString) ? "" : "_search");
        keyFirstVisibleItemY = keyFirstVisibleItemId + "_y";
        keyMinSentDate = keyFirstVisibleItemId + "_last";
        keyQueryString = keyFirstVisibleItemId + "_query_string";
    }

    void save() {
        final String method = "save";
        if (mListView == null || mAdapter == null || mListParameters.isEmpty() || mAdapter.getCount() == 0) {
            MyLog.v(this, method + "; skipped");
            return;
        }
        TimelineAdapter la = mAdapter;
        int firstVisiblePosition = mListView.getFirstVisiblePosition();
        int itemCount = la.getCount();
        if (firstVisiblePosition >= itemCount) {
            firstVisiblePosition = itemCount - 1;
        }
        long firstVisibleItemId = 0;
        int lastPosition = -1;
        long minSentDate = 0;
        int y = 0;
        if (firstVisiblePosition >= 0) {
            firstVisibleItemId = la.getItemId(firstVisiblePosition);
            y = LoadableListActivity.getYOfPosition(mListView, la, firstVisiblePosition);
            MyLog.v(this, method + "; firstVisiblePos:" + firstVisiblePosition + " of " + itemCount
                    + ", id:" + firstVisibleItemId + ", y:" + y);
            lastPosition = mListView.getLastVisiblePosition() + 10;
            if (lastPosition >= itemCount) {
                lastPosition = itemCount - 1;
            }
            minSentDate = la.getItem(lastPosition).sentDate;
        }

        if (firstVisibleItemId <= 0) {
            clear();
        } else {
            saveTLPosition(firstVisibleItemId, minSentDate, y);
        }
        if (MyLog.isVerboseEnabled()) {
            String msgLog = " key=" + keyFirstVisibleItemId
                    + (TextUtils.isEmpty(queryString) ? "" : ", q='" + queryString + "'")
                    + ", id:" + firstVisibleItemId
                    + ", y:" + y
                    + " at pos=" + firstVisiblePosition
                    + ", minDate=" + minSentDate
                    + " at pos=" + lastPosition + " of " + itemCount;
            if (firstVisibleItemId <= 0) {
                MyLog.v(this, method + "; failed " + msgLog
                        + "\n no visible items for " + mListParameters.timelineTitle.toString());
            } else {
                MyLog.v(this, method + "; succeeded " + msgLog);
            }
        }

    }
    
    private void saveTLPosition(long firstVisibleItemId, long minSentDate, int y) {
        sp.edit().putLong(keyFirstVisibleItemId, firstVisibleItemId)
            .putInt(keyFirstVisibleItemY, y)
            .putLong(keyMinSentDate, minSentDate)
            .putString(keyQueryString, queryString).apply();
    }

    public TLPosition getTLPosition() {
        TLPosition tlPosition = new TLPosition();
        String queryString1 = sp.getString(keyQueryString, "");
        if (queryString.equals(queryString1)) {
            tlPosition.firstVisibleItemId = sp.getLong(keyFirstVisibleItemId, NOT_STORED);
            tlPosition.y = sp.getInt(keyFirstVisibleItemY, NOT_STORED);
            tlPosition.minSentDate = sp.getLong(keyMinSentDate, NOT_STORED);
            tlPosition.queryString = queryString1;
        }
        return tlPosition;
    }

    void clear() {
        sp.edit().remove(keyFirstVisibleItemId)
                .remove(keyFirstVisibleItemY)
                .remove(keyMinSentDate)
                .remove(keyQueryString).apply();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "Position forgot key=" + keyFirstVisibleItemId);
        }
    }
    
    /**
     * Restore (the first visible item) position saved for this timeline
     */
    public void restore() {
        final String method = "restore";
        if (mListView == null || mAdapter == null || mListParameters.isEmpty() || mAdapter.getCount() == 0) {
            MyLog.v(this, method + "; skipped");
            return;
        }
        boolean restored = false;
        int position = -1;
        TLPosition tlPosition = getTLPosition();
        try {
            if (tlPosition.firstVisibleItemId > 0) {
                position = getPositionById(tlPosition.firstVisibleItemId);
            }
            if (position >= 0) {
                mListView.setSelectionFromTop(position, tlPosition.y);
                restored = true;
            } else {
                // There is no stored position
                if (mListParameters.whichPage.isYoungest()
                        || mListParameters.hasSearchQuery()
                        || mListView.getCount() < 10) {
                    // In search mode start from the most recent message!
                    position = 0;
                } else {
                    position = mListView.getCount() - 10;

                }
                if (position >= 0) {
                    setPosition(mListView, position);
                }
            }
        } catch (Exception e) {
            MyLog.v(this, method, e);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; " + (restored ? "succeeded" : "failed" )
                    + " key=" + keyFirstVisibleItemId
                    + (TextUtils.isEmpty(queryString) ? "" : ", q='" + queryString + "'")
                    + ", id:" + tlPosition.firstVisibleItemId + ", y:" + tlPosition.y
                    + ", at pos=" + position + " of " + mListView.getCount());
        }
        if (!restored) {
            clear();
        }
        mAdapter.setPositionRestored(true);
    }

    public static void setPosition(ListView listView, int position) {
        if (listView == null) {
            return;
        }
        int viewHeight = listView.getHeight();
        int childHeight = 30;
        int y = viewHeight - childHeight;
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(TAG, "Set position of " + position + " item to " + y + " px");
        }
        listView.setSelectionFromTop(position, y);
    }

    /**
     * @return the position in the list or -1 if the item was not found
     */
    private int getPositionById(long itemId) {
        if (mAdapter == null) {
            return -1;
        }
        return mAdapter.getPositionById(itemId);
    }
}