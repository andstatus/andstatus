/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.database.CommandTable;
import org.andstatus.app.database.TimelineTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.ContentValuesUtils;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class Timeline implements Comparable<Timeline> {
    private volatile long id;

    private final TimelineType timelineType;
    /** "Authenticated User" used to retrieve/post to... this Timeline */
    private final MyAccount myAccount;
    /** A User as a parameter of this timeline.
     * This may be the same the Authenticated User ({@link #myAccount})
     * or some other User e.g. to get a list of messages by some other person/user of the Social Network
     */
    private final long userId;
    /** The Social Network of this timeline. Some timelines don't depend on
     * an Authenticated User ({@link #myAccount}), e.g. {@link TimelineType#PUBLIC} - this
     * timeline may be fetched by any authenticated user of this Social Network */
    private final Origin origin;
    /** Pre-fetched string to be used to present in UI */
    private String userInTimeline = "";
    /** This may be used e.g. to search {@link TimelineType#PUBLIC} timeline */
    @NonNull
    private final String searchQuery;

    /** The timeline combines messages from all accounts
     * or from Social Networks, e.g. Search in all Social Networks */
    private final boolean isCombined;

    /** If this timeline can be synced */
    private final boolean isSyncable;
    /** Is it possible to sync this timeline via usage of one or more (child, not combined...)
     * timelines for individual accounts */
    private final boolean isSyncableForAccounts;
    /** Is it possible to sync this timeline via usage of one or more (child, not combined...)
     * timelines for individual Social networks */
    private final boolean isSyncableForOrigins;

    /** If the timeline is synced automatically */
    private volatile boolean isSyncedAutomatically = false;

    /** If the timeline should be shown in a Timeline selector */
    private volatile boolean isDisplayedInSelector = false;
    /** Used for sorting timelines in a selector */
    private volatile long selectorOrder = 100;

    /** When this timeline was last time successfully synced */
    private volatile long syncSucceededDate = 0;
    /** When last sync error occurred */
    private volatile long syncFailedDate = 0;
    /** Error message at {@link #syncFailedDate} */
    private volatile String errorMessage = "";

    /** Number of successful sync operations: "Synced {@link #syncedTimesCount} times" */
    private volatile long syncedTimesCount = 0;
    /** Number of failed sync operations */
    private volatile long syncFailedTimesCount = 0;
    private volatile long newItemsCount = 0;
    private volatile long countSince = 0;

    /** Accumulated numbers for statistics. They are reset by a user's request */
    private volatile long syncedTimesCountTotal = 0;
    private volatile long syncFailedTimesCountTotal = 0;
    private volatile long newItemsCountTotal = 0;

    /** Timeline position of the youngest ever downloaded message */
    private volatile String youngestPosition = "";
    /** Date of the item corresponding to the {@link #youngestPosition} */
    private volatile long youngestItemDate = 0;
    /** Last date when youngest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update
     */
    private volatile long youngestSyncedDate = 0;

    /** Timeline position of the oldest ever downloaded message */
    private volatile String oldestPosition = "";
    /** Date of the item corresponding to the {@link #oldestPosition} */
    private volatile long oldestItemDate = 0;
    /** Last date when oldest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update
     */
    private volatile long oldestSyncedDate = 0;

    /** Position of the timeline, which a User viewed  */
    private volatile long visibleItemId = 0;
    private volatile int visibleY = 0;
    private volatile long visibleOldestDate = 0;

    private volatile boolean changed = false;

    public static Timeline getTimeline(TimelineType timelineType, MyAccount myAccount, long userId, Origin origin) {
        return getTimeline(MyContextHolder.get(), 0, timelineType, myAccount, userId, origin, "");
    }

    public static Timeline getTimeline(MyContext myContext, long id, TimelineType timelineType,
                                       MyAccount myAccount, long userId, Origin origin,
                                       String searchQuery) {
        Timeline timeline = new Timeline(myContext, id, timelineType, myAccount, userId, origin, searchQuery);
        if (timeline.isValid()) {
            return myContext.persistentTimelines().fromNewTimeLine(timeline);
        }
        return timeline;
    }

    private Timeline(MyContext myContext, long id, TimelineType timelineType, MyAccount myAccount,
                     long userId, Origin origin, String searchQuery) {
        this.id = id;
        this.myAccount = fixedMyAccount(myContext, timelineType, myAccount, userId);
        this.userId = fixedUserId(timelineType, userId);
        this.origin = fixedOrigin(myContext, timelineType, myAccount, userId, origin);
        this.searchQuery = TextUtils.isEmpty(searchQuery) ? "" : searchQuery;
        this.timelineType = fixedTimelineType(timelineType);
        this.isCombined = calcIsCombined(this.timelineType, this.origin, this.myAccount);
        this.isSyncable = calcIsSyncable(myContext);
        this.isSyncableForAccounts = calcIsSyncableForAccounts(myContext);
        this.isSyncableForOrigins = calcIsSyncableForOrigins(myContext);
    }

    private boolean calcIsSyncable(MyContext myContext) {
        if (isCombined() || !timelineType.isSyncable()) {
            return false;
        }
        MyAccount myAccountToSync = getMyAccountToSync(myContext);
        return myAccountToSync.isValidAndSucceeded() && myAccountToSync.isSyncedAutomatically()
                && myAccountToSync.getOrigin().getOriginType().isTimelineTypeSyncable(timelineType);
    }

    /** Returns the best MyAccount to be used to sync this Timeline */
    @NonNull
    public MyAccount getMyAccountToSync(MyContext myContext) {
        MyAccount myAccount = getMyAccount();
        if (!myAccount.isValid()) {
            Origin origin = getOrigin();
            if (origin.isValid()) {
                myAccount = myContext.persistentAccounts().getFirstSucceededForOriginId(origin.getId());
            }
        }
        return myAccount;
    }

    private boolean calcIsSyncableForAccounts(MyContext myContext) {
        return isCombined &&
                timelineType.isSyncable() && timelineType.canBeCombinedForMyAccounts() &&
                myContext.persistentAccounts().getFirstSucceededForOriginId(0).isValidAndSucceeded();
    }

    private boolean calcIsSyncableForOrigins(MyContext myContext) {
        return isCombined &&
                timelineType.isSyncable() && timelineType.canBeCombinedForOrigins() &&
                myContext.persistentAccounts().getFirstSucceededForOriginId(0).isValidAndSucceeded();
    }

    public static Timeline fromCursor(MyContext myContext, Cursor cursor) {
        Timeline timeline = new Timeline(
                myContext,
                0, TimelineType.load(DbUtils.getString(cursor, TimelineTable.TIMELINE_TYPE)),
                myContext.persistentAccounts()
                        .fromUserId(DbUtils.getLong(cursor, TimelineTable.ACCOUNT_ID)),
                DbUtils.getLong(cursor, TimelineTable.USER_ID),
                myContext.persistentOrigins()
                        .fromId(DbUtils.getLong(cursor, TimelineTable.ORIGIN_ID)),
                DbUtils.getString(cursor, TimelineTable.SEARCH_QUERY));

        timeline.id = DbUtils.getLong(cursor, TimelineTable._ID);
        timeline.userInTimeline = DbUtils.getString(cursor, TimelineTable.USER_IN_TIMELINE);
        timeline.setSyncedAutomatically(DbUtils.getBoolean(cursor, TimelineTable.IS_SYNCED_AUTOMATICALLY));
        timeline.isDisplayedInSelector = DbUtils.getBoolean(cursor, TimelineTable.IS_DISPLAYED_IN_SELECTOR);
        timeline.selectorOrder = DbUtils.getLong(cursor, TimelineTable.SELECTOR_ORDER);

        timeline.syncSucceededDate = DbUtils.getLong(cursor, TimelineTable.SYNC_SUCCEEDED_DATE);
        timeline.syncFailedDate = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_DATE);
        timeline.errorMessage = DbUtils.getString(cursor, TimelineTable.ERROR_MESSAGE);

        timeline.syncedTimesCount = DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT);
        timeline.syncFailedTimesCount = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT);
        timeline.newItemsCount = DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT);
        timeline.countSince = DbUtils.getLong(cursor, TimelineTable.COUNT_SINCE);
        timeline.syncedTimesCountTotal = DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT_TOTAL);
        timeline.syncFailedTimesCountTotal = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL);
        timeline.newItemsCountTotal = DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT_TOTAL);

        timeline.youngestPosition = DbUtils.getString(cursor, TimelineTable.YOUNGEST_POSITION);
        timeline.youngestItemDate = DbUtils.getLong(cursor, TimelineTable.YOUNGEST_ITEM_DATE);
        timeline.youngestSyncedDate = DbUtils.getLong(cursor, TimelineTable.YOUNGEST_SYNCED_DATE);
        timeline.oldestPosition = DbUtils.getString(cursor, TimelineTable.OLDEST_POSITION);
        timeline.oldestItemDate = DbUtils.getLong(cursor, TimelineTable.OLDEST_ITEM_DATE);
        timeline.oldestSyncedDate = DbUtils.getLong(cursor, TimelineTable.OLDEST_SYNCED_DATE);

        timeline.visibleItemId = DbUtils.getLong(cursor, TimelineTable.VISIBLE_ITEM_ID);
        timeline.visibleY = DbUtils.getInt(cursor, TimelineTable.VISIBLE_Y);
        timeline.visibleOldestDate = DbUtils.getLong(cursor, TimelineTable.VISIBLE_OLDEST_DATE);

        return timeline;
    }

    public static Timeline fromCommandCursor(MyContext myContext, Cursor cursor) {
        return getTimeline(
                myContext,
                DbUtils.getLong(cursor, CommandTable.TIMELINE_ID),
                TimelineType.load(DbUtils.getString(cursor, CommandTable.TIMELINE_TYPE)),
                myContext.persistentAccounts().fromUserId(DbUtils.getLong(cursor, CommandTable.ACCOUNT_ID)),
                DbUtils.getLong(cursor, CommandTable.USER_ID),
                myContext.persistentOrigins().fromId(DbUtils.getLong(cursor, CommandTable.ORIGIN_ID)),
                DbUtils.getString(cursor, CommandTable.SEARCH_QUERY)
        );
    }

    public static Timeline getEmpty(MyAccount myAccount) {
        return getTimeline(TimelineType.UNKNOWN, myAccount, 0, null);
    }

    public static List<Timeline> addDefaultForAccount(MyContext myContext, MyAccount myAccount) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.defaultMyAccountTimelineTypes) {
            saveNewDefaultTimeline(myContext, getTimeline(myContext, 0, timelineType, myAccount, 0, null, ""));
        }
        return timelines;
    }

    public static Collection<Timeline> addDefaultForOrigin(MyContext myContext, Origin origin) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.defaultOriginTimelineTypes) {
            if (origin.getOriginType().isTimelineTypeSyncable(timelineType)) {
                saveNewDefaultTimeline(myContext, getTimeline(myContext, 0, timelineType, null, 0, origin, ""));
            }
        }
        return timelines;
    }

    public static List<Timeline> addDefaultCombined(MyContext myContext) {
        List<Timeline> timelines = new ArrayList<>();
        for (TimelineType timelineType : TimelineType.values()) {
            if (timelineType.isSelectable()) {
                saveNewDefaultTimeline(myContext, getTimeline(myContext, 0, timelineType, null, 0, null, ""));
            }
        }
        return timelines;
    }

    protected static void saveNewDefaultTimeline(MyContext myContext, Timeline timeline) {
        timeline.isDisplayedInSelector = true;
        timeline.selectorOrder = timeline.getTimelineType().ordinal();
        timeline.setSyncedAutomatically(timeline.getTimelineType().isSyncedAutomaticallyByDefault());
        timeline.save(myContext);
    }

    public static Timeline fromBundle(MyContext myContext, Bundle bundle) {
        MyAccount myAccount = MyAccount.fromBundle(bundle);
        Timeline timeline = getEmpty(myAccount);
        if (bundle != null) {
            timeline = MyContextHolder.get().persistentTimelines().fromId(
                    bundle.getLong(IntentExtra.TIMELINE_ID.key));
            if (timeline.isEmpty()) {
                timeline = getTimeline(myContext, 0,
                        TimelineType.load(bundle.getString(IntentExtra.TIMELINE_TYPE.key)),
                        myAccount, bundle.getLong(IntentExtra.USER_ID.key),
                        MyContextHolder.get().persistentOrigins().fromId(
                                BundleUtils.fromBundle(bundle, IntentExtra.ORIGIN_ID)),
                        BundleUtils.getString(bundle, IntentExtra.SEARCH_QUERY));
            }
        }
        return timeline;
    }

    public static Timeline fromParsedUri(MyContext myContext, ParsedUri parsedUri, String searchQueryIn) {
        String searchQuery = searchQueryIn;
        if (TextUtils.isEmpty(searchQuery)) {
            searchQuery = parsedUri.getSearchQuery();
        }
        Timeline timeline = getTimeline(myContext, 0,
                parsedUri.getTimelineType(),
                myContext.persistentAccounts().fromUserId(parsedUri.getAccountUserId()),
                parsedUri.getUserId(),
                myContext.persistentOrigins().fromId(parsedUri.getOriginId()),
                searchQuery);
        if (timeline.getTimelineType() == TimelineType.UNKNOWN) {
            MyLog.e(Timeline.class, "fromParsedUri; uri:" + parsedUri.getUri() + "; " + timeline);
        }
        return timeline;
    }

    private boolean calcIsCombined(TimelineType timelineType, Origin origin, MyAccount myAccount) {
        return timelineType.isAtOrigin() ? !origin.isValid() : !myAccount.isValid();
    }

    private MyAccount fixedMyAccount(MyContext myContext, TimelineType timelineType, MyAccount myAccountIn, long userIdIn) {
        if (timelineType == null) {
            return MyAccount.getEmpty();
        }
        MyAccount myAccount = myAccountIn == null ? MyAccount.getEmpty() : myAccountIn;
        long userId = timelineType.isForUser() ? userIdIn : 0;
        if (myContext.persistentAccounts().isAccountUserId(userId)) {
            myAccount = myContext.persistentAccounts().fromUserId(userId);
        }
        if (timelineType.isAtOrigin() &&
                !timelineType.isForUser() &&
                userId == 0 || (userId != 0 && !myContext.persistentAccounts().isAccountUserId(userId))
                ) {
            return MyAccount.getEmpty();
        }
        return myAccount;
    }

    private long fixedUserId(TimelineType timelineType, long userId) {
        if (userId == 0 && myAccount.isValid() && timelineType.isForUser()) {
            return myAccount.getUserId();
        }
        return userId;
    }

    private Origin fixedOrigin(MyContext myContext, TimelineType timelineType, MyAccount myAccountIn, long userId, Origin origin) {
        Origin fixedOrigin = origin == null ? Origin.getEmpty() : origin;
        MyAccount ma = myContext.persistentAccounts().fromUserId(userId);
        if (!ma.isValid() && myAccountIn != null) {
            ma = myAccountIn;
        }
        if (ma.isValid()) {
            if (fixedOrigin.isValid() && fixedOrigin != ma.getOrigin()) {
                fixedOrigin = Origin.getEmpty();
            }
            if (timelineType.isAtOrigin() || !fixedOrigin.isValid()) {
                fixedOrigin = ma.getOrigin();
            }
        }
        return fixedOrigin;
    }

    private TimelineType fixedTimelineType(TimelineType timelineTypeIn) {
        TimelineType timelineType = timelineTypeIn;
        if (timelineType == null) {
            return TimelineType.UNKNOWN;
        }
        boolean isCombined = false;
        if (timelineType.isAtOrigin()) {
            if (!origin.isValid()) {
                isCombined = true;
            }
        } else {
            if (!myAccount.isValid()) {
                isCombined = true;
            }
        }
        if (timelineType.isForUser() && userId == 0) {
            isCombined = true;
        }
        if (isCombined != calcIsCombined(timelineType, origin, myAccount)) {
            return TimelineType.UNKNOWN;
        }
        if (timelineType.isForSearchQuery() && !hasSearchQuery()) {
            if (timelineType == TimelineType.SEARCH) {
                timelineType = TimelineType.EVERYTHING;
            } else {
                return TimelineType.UNKNOWN;
            }
        } else if (!timelineType.isForSearchQuery() && hasSearchQuery()) {
            if (timelineType == TimelineType.EVERYTHING) {
                timelineType = TimelineType.SEARCH;
            }
        }

        if (timelineType.isForUser()) {
            if (myAccount.getUserId() == userId) {
                switch (timelineType) {
                    case USER:
                        return TimelineType.SENT;
                    case FRIENDS:
                        return TimelineType.MY_FRIENDS;
                    case FOLLOWERS:
                        return TimelineType.MY_FOLLOWERS;
                    default:
                        break;
                }
            } else {
                switch (timelineType) {
                    case SENT:
                        return TimelineType.USER;
                    case MY_FRIENDS:
                        return TimelineType.FRIENDS;
                    case MY_FOLLOWERS:
                        return TimelineType.FOLLOWERS;
                    default:
                        break;
                }
            }
        }
        return timelineType;
    }

    @Override
    public int compareTo(Timeline another) {
        return selectorOrder == another.selectorOrder ? 0 :
                (selectorOrder >= another.selectorOrder ? 1 : -1);
    }

    public void toContentValues(ContentValues values) {
        ContentValuesUtils.putNotZero(values, TimelineTable._ID, id);
        values.put(TimelineTable.TIMELINE_TYPE, timelineType.save());
        values.put(TimelineTable.ACCOUNT_ID, myAccount.getUserId());
        values.put(TimelineTable.USER_ID, userId);
        values.put(TimelineTable.USER_IN_TIMELINE, userInTimeline);
        values.put(TimelineTable.ORIGIN_ID, origin.getId());
        values.put(TimelineTable.SEARCH_QUERY, searchQuery);

        values.put(TimelineTable.IS_SYNCED_AUTOMATICALLY, isSyncedAutomatically);
        values.put(TimelineTable.IS_DISPLAYED_IN_SELECTOR, isDisplayedInSelector);
        values.put(TimelineTable.SELECTOR_ORDER, selectorOrder);

        values.put(TimelineTable.SYNC_SUCCEEDED_DATE, syncSucceededDate);
        values.put(TimelineTable.SYNC_FAILED_DATE, syncFailedDate);
        values.put(TimelineTable.ERROR_MESSAGE, errorMessage);

        values.put(TimelineTable.SYNCED_TIMES_COUNT, syncedTimesCount);
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT, syncFailedTimesCount);
        values.put(TimelineTable.NEW_ITEMS_COUNT, newItemsCount);
        values.put(TimelineTable.COUNT_SINCE, countSince);
        values.put(TimelineTable.SYNCED_TIMES_COUNT_TOTAL, syncedTimesCountTotal);
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL, syncFailedTimesCountTotal);
        values.put(TimelineTable.NEW_ITEMS_COUNT_TOTAL, newItemsCountTotal);

        values.put(TimelineTable.YOUNGEST_POSITION, youngestPosition);
        values.put(TimelineTable.YOUNGEST_ITEM_DATE, youngestItemDate);
        values.put(TimelineTable.YOUNGEST_SYNCED_DATE, youngestSyncedDate);
        values.put(TimelineTable.OLDEST_POSITION, oldestPosition);
        values.put(TimelineTable.OLDEST_ITEM_DATE, oldestItemDate);
        values.put(TimelineTable.OLDEST_SYNCED_DATE, oldestSyncedDate);

        values.put(TimelineTable.VISIBLE_ITEM_ID, visibleItemId);
        values.put(TimelineTable.VISIBLE_Y, visibleY);
        values.put(TimelineTable.VISIBLE_OLDEST_DATE, visibleOldestDate);
    }

    public void toCommandContentValues(ContentValues values) {
        values.put(CommandTable.TIMELINE_ID, id);
        values.put(CommandTable.TIMELINE_TYPE, timelineType.save());
        values.put(CommandTable.ACCOUNT_ID, myAccount.getUserId());
        values.put(CommandTable.USER_ID, userId);
        values.put(CommandTable.ORIGIN_ID, origin.getId());
        values.put(CommandTable.SEARCH_QUERY, searchQuery);
    }

    public Timeline fromSearch(MyContext myContext, boolean globalSearch) {
        Timeline timeline = this;
        if (globalSearch) {
            timeline = getTimeline(myContext, 0, TimelineType.SEARCH,
                    this.getMyAccount(), this.getUserId(), this.getOrigin(),
                    this.getSearchQuery());
        }
        return timeline;
    }

    public Timeline fromIsCombined(MyContext myContext, boolean isCombined) {
        if (this.isCombined() == isCombined) {
            return this;
        }
        Origin origin;
        MyAccount myAccount;
        if (isCombined) {
            origin = Origin.getEmpty();
            myAccount = MyAccount.getEmpty();
        } else {
            origin = myContext.persistentAccounts().getCurrentAccount().getOrigin();
            myAccount = myContext.persistentAccounts().getCurrentAccount();
        }
        return getTimeline(myContext, 0, timelineType, myAccount, 0, origin, searchQuery);
    }

    public Timeline fromMyAccount(MyContext myContext, MyAccount myAccount) {
        if (this.myAccount.equals(myAccount) ||
                (isCombined() && !this.myAccount.isValid())) {
            return this;
        }
        long userId = this.userId;
        Origin origin = this.origin;
        if (!isUserDifferentFromAccount()) {
            userId = 0;
        }
        if (!this.myAccount.getOrigin().equals(myAccount.getOrigin())) {
            origin = Origin.getEmpty();
        }
        return getTimeline(myContext, 0, getTimelineType(), myAccount, userId, origin, searchQuery);
    }

    public boolean isEmpty() {
        return timelineType == TimelineType.UNKNOWN;
    }

    public boolean isValid() {
        return timelineType != TimelineType.UNKNOWN;
    }

    public long getId() {
        return id;
    }

    public TimelineType getTimelineType() {
        return timelineType;
    }

    public boolean isCombined() {
        return isCombined;
    }

    public Origin getOrigin() {
        return origin;
    }

    public MyAccount getMyAccount() {
        return myAccount;
    }

    public boolean isDisplayedInSelector() {
        return isDisplayedInSelector;
    }

    public void setDisplayedInSelector(boolean displayedInSelector) {
        if (this.isDisplayedInSelector != displayedInSelector) {
            this.isDisplayedInSelector = displayedInSelector;
            changed = true;
        }
    }

    public long getSelectorOrder() {
        return selectorOrder;
    }

    public long save(MyContext myContext) {
        if (needToLoadUserInTimeline()) {
            changed = true;
        }
        if (isValid() && (id == 0 || changed) && myContext.isReady()) {
            boolean isNew = id == 0;
            saveInternal();
            if (isNew && id != 0) {
                myContext.persistentTimelines().addNew(this);
            }
        }
        return id;
    }

    private long saveInternal() {
        if (needToLoadUserInTimeline()) {
            userInTimeline = MyQuery.userIdToName(userId, MyPreferences.getUserInTimeline());
        }
        ContentValues contentValues = new ContentValues();
        toContentValues(contentValues);
        if (getId() == 0) {
            id = DbUtils.addRowWithRetry(TimelineTable.TABLE_NAME, contentValues, 3);
            MyLog.v(this, "Added " + this);
        } else {
            DbUtils.updateRowWithRetry(TimelineTable.TABLE_NAME, getId(), contentValues, 3);
        }
        changed = false;
        return getId();
    }

    public boolean needToLoadUserInTimeline() {
        return userId != 0 && isUserDifferentFromAccount() && TextUtils.isEmpty(userInTimeline);
    }

    public void delete() {
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.d(this, "delete; Database is unavailable");
        } else {
            String sql = "DELETE FROM " + TimelineTable.TABLE_NAME + " WHERE _ID=" + getId();
            db.execSQL(sql);
            MyLog.v(this, "Timeline deleted: " + this);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Timeline{");
        if (myAccount.isValid()) {
            builder.append(myAccount.getAccountName());
            if (!myAccount.getOrigin().equals(origin) && origin.isValid()) {
                builder.append(", origin:" + origin.getName());
            }
        } else {
            builder.append(origin.isValid() ? origin.getName() : "(all origins)");
        }
        if (timelineType != TimelineType.UNKNOWN) {
            builder.append(", type:" + timelineType.save());
        }
        if (!TextUtils.isEmpty(userInTimeline)) {
            builder.append(", user:'" + userInTimeline + "'");
        } else if (userId != 0) {
            builder.append(", userId:" + userId);
        }
        if (hasSearchQuery()) {
            builder.append(" search:'" + getSearchQuery() + "'");
        }
        if ( id != 0) {
            builder.append(", id:" + id);
        }
        builder.append('}');
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Timeline)) return false;

        Timeline timeline = (Timeline) o;

        if (timelineType != timeline.timelineType) return false;
        if (!origin.equals(timeline.origin)) return false;
        if (!myAccount.equals(timeline.myAccount)) return false;
        if (userId != timeline.userId) return false;
        return searchQuery.equals(timeline.searchQuery);
    }

    @Override
    public int hashCode() {
        int result = timelineType.hashCode();
        result = 31 * result + origin.hashCode();
        result = 31 * result + myAccount.hashCode();
        result = 31 * result + (int) (userId ^ (userId >>> 32));
        result = 31 * result + searchQuery.hashCode();
        return result;
    }

    public long getUserId() {
        return userId;
    }

    public boolean hasSearchQuery() {
        return !TextUtils.isEmpty(getSearchQuery());
    }

    @NonNull
    public String getSearchQuery() {
        return searchQuery;
    }

    public void toBundle(Bundle bundle) {
        BundleUtils.putNotZero(bundle, IntentExtra.TIMELINE_ID, id);
        if (myAccount.isValid()) {
            bundle.putString(IntentExtra.ACCOUNT_NAME.key, myAccount.getAccountName());
        }
        if (timelineType != TimelineType.UNKNOWN) {
            bundle.putString(IntentExtra.TIMELINE_TYPE.key, timelineType.save());
        }
        BundleUtils.putNotZero(bundle, IntentExtra.ORIGIN_ID, origin.getId());
        BundleUtils.putNotZero(bundle, IntentExtra.USER_ID, userId);
        BundleUtils.putNotEmpty(bundle, IntentExtra.SEARCH_QUERY, searchQuery);
    }

    public boolean isUserDifferentFromAccount() {
        return userId != 0 && myAccount.getUserId() != userId;
    }

    /**
     * @return true if it's time to auto update this timeline
     */
    public boolean isTimeToAutoSync() {
        long frequencyMs = MyPreferences.getSyncFrequencyMs();
        long passedMs = System.currentTimeMillis() - getSyncSucceededDate();
        boolean blnOut = passedMs > frequencyMs;

        if (blnOut && MyLog.isVerboseEnabled()) {
            MyLog.v(this, "It's time to auto update " + this +
                    ". " +
                    java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(passedMs) +
                    " minutes passed.");
        }
        return blnOut;
    }

    public String getYoungestPosition() {
        return youngestPosition;
    }

    public void setYoungestPosition(String youngestPosition) {
        this.youngestPosition = youngestPosition;
        changed = true;
    }

    public long getYoungestItemDate() {
        return youngestItemDate;
    }

    public void setYoungestItemDate(long youngestItemDate) {
        this.youngestItemDate = youngestItemDate;
        changed = true;
    }

    public long getYoungestSyncedDate() {
        return youngestSyncedDate;
    }

    public void setYoungestSyncedDate(long youngestSyncedDate) {
        this.youngestSyncedDate = youngestSyncedDate;
        changed = true;
    }

    public long getVisibleItemId() {
        return visibleItemId;
    }

    public void setVisibleItemId(long visibleItemId) {
        if (this.visibleItemId != visibleItemId) {
            changed = true;
            this.visibleItemId = visibleItemId;
        }
    }

    public int getVisibleY() {
        return visibleY;
    }

    public void setVisibleY(int visibleY) {
        if (this.visibleY != visibleY) {
            changed = true;
            this.visibleY = visibleY;
        }
    }

    public long getVisibleOldestDate() {
        return visibleOldestDate;
    }

    public void setVisibleOldestDate(long visibleOldestDate) {
        if (this.visibleOldestDate != visibleOldestDate) {
            changed = true;
            this.visibleOldestDate = visibleOldestDate;
        }
    }

    public boolean isSyncedAutomatically() {
        return isSyncedAutomatically;
    }

    public void setSyncedAutomatically(boolean isSyncedAutomatically) {
        if (this.isSyncedAutomatically != isSyncedAutomatically && isSyncable()) {
            this.isSyncedAutomatically = isSyncedAutomatically;
            changed = true;
        }
    }

    public boolean isChanged() {
        return changed;
    }

    public Timeline cloneForAccount(MyContext myContext, MyAccount ma) {
        long userId = getMyAccount().getUserId() != getUserId() ? getUserId() : 0;
        return getTimeline(myContext, 0, getTimelineType(), ma, userId, null, getSearchQuery());
    }

    public Timeline cloneForOrigin(MyContext myContext, Origin origin) {
        return getTimeline(myContext, 0, getTimelineType(), null, 0, origin, getSearchQuery());
    }

    public void onSyncEnded(CommandResult result) {
        if (result.hasError()) {
            syncFailedDate = System.currentTimeMillis();
            if (!TextUtils.isEmpty(result.getMessage())) {
                errorMessage = result.getMessage();
            }
            syncFailedTimesCount++;
            syncFailedTimesCountTotal++;
        } else {
            syncSucceededDate = System.currentTimeMillis();
            syncedTimesCount++;
            syncedTimesCountTotal++;
        }
        if (result.getMessagesAdded() > 0) {
            newItemsCount += result.getMessagesAdded();
            newItemsCountTotal += result.getMessagesAdded();
        }
        changed = true;
    }

    public long getSyncSucceededDate() {
        return syncSucceededDate;
    }

    public void setSyncSucceededDate(long syncSucceededDate) {
        this.syncSucceededDate = syncSucceededDate;
    }

    public boolean isSyncable() {
        return isSyncable;
    }

    public boolean isSyncableForAccounts() {
        return isSyncableForAccounts;
    }

    public boolean isSyncableForOrigins() {
        return isSyncableForOrigins;
    }

    public long getNewItemsCount() {
        return newItemsCount;
    }

    public void setNewItemsCount(long newItemsCount) {
        this.newItemsCount = newItemsCount;
    }

    public long getNewItemsCountTotal() {
        return newItemsCountTotal;
    }

    public void setNewItemsCountTotal(long newItemsCountTotal) {
        this.newItemsCountTotal = newItemsCountTotal;
    }

    public long getSyncedTimesCount() {
        return syncedTimesCount;
    }

    public void setSyncedTimesCount(long syncedTimesCount) {
        this.syncedTimesCount = syncedTimesCount;
    }

    public long getSyncedTimesCountTotal() {
        return syncedTimesCountTotal;
    }

    public void setSyncedTimesCountTotal(long syncedTimesCountTotal) {
        this.syncedTimesCountTotal = syncedTimesCountTotal;
    }

    public long getSyncFailedDate() {
        return syncFailedDate;
    }

    public void setSyncFailedDate(long syncFailedDate) {
        this.syncFailedDate = syncFailedDate;
    }

    public long getSyncFailedTimesCount() {
        return syncFailedTimesCount;
    }

    public void setSyncFailedTimesCount(long syncFailedTimesCount) {
        this.syncFailedTimesCount = syncFailedTimesCount;
    }

    public long getSyncFailedTimesCountTotal() {
        return syncFailedTimesCountTotal;
    }

    public void setSyncFailedTimesCountTotal(long syncFailedTimesCountTotal) {
        this.syncFailedTimesCountTotal = syncFailedTimesCountTotal;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getLastSyncedDate() {
        return syncSucceededDate > 0 ? syncSucceededDate : syncFailedDate;
    }

    @NonNull
    public String getUserInTimeline() {
        if (TextUtils.isEmpty(userInTimeline)) {
            if (needToLoadUserInTimeline()) {
                return "...";
            } else {
                return "";
            }
        } else {
            return userInTimeline;
        }
    }
}
