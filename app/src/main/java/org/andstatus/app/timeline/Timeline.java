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
import org.andstatus.app.data.SqlWhere;
import org.andstatus.app.database.CommandTable;
import org.andstatus.app.database.TimelineTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.ContentValuesUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author yvolk@yurivolkov.com
 */
public class Timeline implements Comparable<Timeline> {
    public static final Timeline EMPTY = new Timeline(MyAccount.EMPTY);
    private static final long MIN_RETRY_PERIOD_MS = TimeUnit.SECONDS.toMillis(30);
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
    /** If this timeline can be synced automatically */
    private final boolean isSyncableAutomatically;
    /** Is it possible to sync this timeline via usage of one or more (child, not combined...)
     * timelines for individual accounts */
    private final boolean isSyncableForAccounts;
    /** Is it possible to sync this timeline via usage of one or more (child, not combined...)
     * timelines for individual Social networks */
    private final boolean isSyncableForOrigins;

    /** If the timeline is synced automatically */
    private volatile boolean isSyncedAutomatically = false;

    /** If the timeline should be shown in a Timeline selector */
    private volatile DisplayedInSelector isDisplayedInSelector = DisplayedInSelector.NEVER;
    /** Used for sorting timelines in a selector */
    private volatile long selectorOrder = 0;

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
    private volatile long downloadedItemsCount = 0;
    private volatile long newItemsCount = 0;
    private volatile long countSince = System.currentTimeMillis();

    /** Accumulated numbers for statistics. They are reset by a user's request */
    private volatile long syncedTimesCountTotal = 0;
    private volatile long syncFailedTimesCountTotal = 0;
    private volatile long downloadedItemsCountTotal = 0;
    private volatile long newItemsCountTotal = 0;

    /** Timeline position of the youngest ever downloaded message */
    @NonNull
    private volatile String youngestPosition = "";
    /** Date of the item corresponding to the {@link #youngestPosition} */
    private volatile long youngestItemDate = 0;
    /** Last date when youngest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update
     */
    private volatile long youngestSyncedDate = 0;

    /** Timeline position of the oldest ever downloaded message */
    @NonNull
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

    private Timeline(MyAccount myAccount) {
        timelineType = TimelineType.UNKNOWN;
        this.myAccount = myAccount;
        userId = 0;
        origin = myAccount.getOrigin();
        searchQuery = "";
        isCombined = calcIsCombined(timelineType, origin, myAccount);
        isSyncable = false;
        isSyncableAutomatically = false;
        isSyncableForAccounts = false;
        isSyncableForOrigins = false;
    }

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
        this.searchQuery = TextUtils.isEmpty(searchQuery) ? "" : searchQuery.trim();
        this.timelineType = fixedTimelineType(timelineType);
        this.isCombined = calcIsCombined(this.timelineType, this.origin, this.myAccount);
        MyAccount myAccountToSync = getMyAccountToSync(myContext);
        this.isSyncable = calcIsSyncable(myAccountToSync);
        this.isSyncableAutomatically = this.isSyncable && myAccountToSync.isSyncedAutomatically();
        this.isSyncableForAccounts = calcIsSyncableForAccounts(myContext);
        this.isSyncableForOrigins = calcIsSyncableForOrigins(myContext);
        this.setDefaultSelectorOrder();
    }

    protected void setDefaultSelectorOrder() {
        setSelectorOrder((timelineType.ordinal() + 1L) * 2 + (isCombined ? 1 : 0));
    }

    private boolean calcIsSyncable(MyAccount myAccountToSync) {
        if (isCombined() || !timelineType.isSyncable()) {
            return false;
        }
        return myAccountToSync.isValidAndSucceeded()
                && myAccountToSync.getOrigin().getOriginType().isTimelineTypeSyncable(timelineType);
    }

    /** Returns the best MyAccount to be used to sync this Timeline */
    @NonNull
    public MyAccount getMyAccountToSync(MyContext myContext) {
        MyAccount myAccount = getMyAccount();
        if (!myAccount.isValid()) {
            Origin origin = getOrigin();
            if (origin.isValid()) {
                myAccount = myContext.persistentAccounts().getFirstSucceededForOrigin(origin);
            }
        }
        return myAccount;
    }

    private boolean calcIsSyncableForAccounts(MyContext myContext) {
        return isCombined &&
                timelineType.isSyncable() && timelineType.canBeCombinedForMyAccounts() &&
                myContext.persistentAccounts().getFirstSucceeded().isValidAndSucceeded();
    }

    private boolean calcIsSyncableForOrigins(MyContext myContext) {
        return isCombined &&
                timelineType.isSyncable() && timelineType.canBeCombinedForOrigins() &&
                myContext.persistentAccounts().getFirstSucceeded().isValidAndSucceeded();
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
        timeline.isDisplayedInSelector = DisplayedInSelector.load(DbUtils.getString(cursor, TimelineTable.DISPLAYED_IN_SELECTOR));
        timeline.selectorOrder = DbUtils.getLong(cursor, TimelineTable.SELECTOR_ORDER);

        timeline.syncSucceededDate = DbUtils.getLong(cursor, TimelineTable.SYNC_SUCCEEDED_DATE);
        timeline.syncFailedDate = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_DATE);
        timeline.errorMessage = DbUtils.getString(cursor, TimelineTable.ERROR_MESSAGE);

        timeline.syncedTimesCount = DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT);
        timeline.syncFailedTimesCount = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT);
        timeline.downloadedItemsCount = DbUtils.getLong(cursor, TimelineTable.DOWNLOADED_ITEMS_COUNT);
        timeline.newItemsCount = DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT);
        timeline.countSince = DbUtils.getLong(cursor, TimelineTable.COUNT_SINCE);
        timeline.syncedTimesCountTotal = DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT_TOTAL);
        timeline.syncFailedTimesCountTotal = DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL);
        timeline.downloadedItemsCountTotal = DbUtils.getLong(cursor, TimelineTable.DOWNLOADED_ITEMS_COUNT_TOTAL);
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
        return new Timeline(myAccount);
    }

    public static Timeline fromBundle(MyContext myContext, Bundle bundle) {
        MyAccount myAccount = MyAccount.fromBundle(bundle);
        Timeline timeline = getEmpty(myAccount);
        if (bundle != null) {
            timeline = myContext.persistentTimelines().fromId(
                    bundle.getLong(IntentExtra.TIMELINE_ID.key));
            if (timeline.isEmpty()) {
                timeline = getTimeline(myContext, 0,
                        TimelineType.load(bundle.getString(IntentExtra.TIMELINE_TYPE.key)),
                        myAccount, bundle.getLong(IntentExtra.USER_ID.key),
                        myContext.persistentOrigins().fromId(
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

    @NonNull
    private MyAccount fixedMyAccount(MyContext myContext, TimelineType timelineType, MyAccount myAccountIn, long userIdIn) {
        if (timelineType == null) {
            return MyAccount.EMPTY;
        }
        MyAccount myAccount = myAccountIn == null ? MyAccount.EMPTY : myAccountIn;
        long userId = timelineType.isForUser() ? userIdIn : 0;
        if (myContext.persistentAccounts().isAccountUserId(userId)) {
            myAccount = myContext.persistentAccounts().fromUserId(userId);
        }
        if (timelineType.isAtOrigin() &&
                !timelineType.isForUser() &&
                userId == 0 || (userId != 0 && !myContext.persistentAccounts().isAccountUserId(userId))
                ) {
            return MyAccount.EMPTY;
        }
        return myAccount;
    }

    private long fixedUserId(TimelineType timelineType, long userId) {
        if (userId == 0 && myAccount.isValid() && timelineType.isForUser()) {
            return myAccount.getUserId();
        }
        return userId;
    }

    @NonNull
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
    public int compareTo(@NonNull Timeline another) {
        int result = CollectionsUtil.compareCheckbox(checkBoxDisplayedInSelector(), another.checkBoxDisplayedInSelector());
        if (result != 0) {
            return result;
        }
        return ((Long) getSelectorOrder()).compareTo(another.getSelectorOrder());
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
        values.put(TimelineTable.DISPLAYED_IN_SELECTOR, isDisplayedInSelector.save());
        values.put(TimelineTable.SELECTOR_ORDER, selectorOrder);

        values.put(TimelineTable.SYNC_SUCCEEDED_DATE, syncSucceededDate);
        values.put(TimelineTable.SYNC_FAILED_DATE, syncFailedDate);
        values.put(TimelineTable.ERROR_MESSAGE, errorMessage);

        values.put(TimelineTable.SYNCED_TIMES_COUNT, syncedTimesCount);
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT, syncFailedTimesCount);
        values.put(TimelineTable.DOWNLOADED_ITEMS_COUNT, downloadedItemsCount);
        values.put(TimelineTable.NEW_ITEMS_COUNT, newItemsCount);
        values.put(TimelineTable.COUNT_SINCE, countSince);
        values.put(TimelineTable.SYNCED_TIMES_COUNT_TOTAL, syncedTimesCountTotal);
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL, syncFailedTimesCountTotal);
        values.put(TimelineTable.DOWNLOADED_ITEMS_COUNT_TOTAL, downloadedItemsCountTotal);
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
            myAccount = MyAccount.EMPTY;
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

    @NonNull
    public Origin getOrigin() {
        return origin;
    }

    @NonNull
    public MyAccount getMyAccount() {
        return myAccount;
    }

    public boolean checkBoxDisplayedInSelector() {
        return !isDisplayedInSelector.equals(DisplayedInSelector.NEVER);
    }

    public DisplayedInSelector isDisplayedInSelector() {
        return isDisplayedInSelector;
    }

    public void setDisplayedInSelector(DisplayedInSelector displayedInSelector) {
        if (this.isDisplayedInSelector != displayedInSelector) {
            this.isDisplayedInSelector = displayedInSelector;
            changed = true;
        }
    }

    public long getSelectorOrder() {
        return selectorOrder;
    }

    public void setSelectorOrder(long selectorOrder) {
        if (this.selectorOrder != selectorOrder) {
            changed = true;
            this.selectorOrder = selectorOrder;
        }
    }

    public long save(MyContext myContext) {
        if (MyAsyncTask.isUiThread()) {
            throw new IllegalStateException("Saving a timeline on the Main thread " + toString());
        }
        if (needToLoadUserInTimeline()) {
            changed = true;
        }
        if (isValid() && (id == 0 || changed) && myContext.isReady()) {
            boolean isNew = id == 0;
            if (isNew) {
                long duplicatedId = findDuplicateInDatabase(myContext);
                if (duplicatedId != 0) {
                    MyLog.i(this, "Found duplicating timeline, id=" + duplicatedId + " for " + this);
                    return duplicatedId;
                }
            }
            saveInternal(myContext);
            if (isNew && id != 0) {
                myContext.persistentTimelines().addNew(this);
            }
        }
        return id;
    }

    private long findDuplicateInDatabase(MyContext myContext) {
        SqlWhere where = new SqlWhere();
        where.append(TimelineTable.TIMELINE_TYPE + "='" + timelineType.save() + "'");
        where.append(TimelineTable.ACCOUNT_ID + "=" + myAccount.getUserId());
        where.append(TimelineTable.ORIGIN_ID + "=" + origin.getId());
        where.append(TimelineTable.USER_ID + "=" + userId);
        where.append(TimelineTable.SEARCH_QUERY + "='" + searchQuery + "'");
        return MyQuery.conditionToLongColumnValue(
                myContext.getDatabase(),
                "findDuplicateInDatabase",
                TimelineTable.TABLE_NAME,
                TimelineTable._ID,
                where.getCondition());
    }

    private long saveInternal(MyContext myContext) {
        if (needToLoadUserInTimeline()) {
            userInTimeline = MyQuery.userIdToName(userId, MyPreferences.getUserInTimeline());
        }
        ContentValues contentValues = new ContentValues();
        toContentValues(contentValues);
        if (getId() == 0) {
            id = DbUtils.addRowWithRetry(myContext, TimelineTable.TABLE_NAME, contentValues, 3);
            MyLog.v(this, "Added " + this +
                    (myContext.isTestRun() ? " from " + MyLog.getStackTrace(new Throwable()) : ""));
        } else {
            DbUtils.updateRowWithRetry(myContext, TimelineTable.TABLE_NAME, getId(), contentValues, 3);
        }
        changed = false;
        return getId();
    }

    public boolean needToLoadUserInTimeline() {
        return userId != 0 && isUserDifferentFromAccount() && TextUtils.isEmpty(userInTimeline);
    }

    public void delete() {
        if (isRequired()) {
            MyLog.d(this, "Cannot delete required timeline: " + this);
            return;
        }
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.d(this, "delete; Database is unavailable");
        } else {
            String sql = "DELETE FROM " + TimelineTable.TABLE_NAME + " WHERE _ID=" + getId();
            db.execSQL(sql);
            MyLog.v(this, "Timeline deleted: " + this);
        }
    }

    /** Required timeline cannot be deleted */
    public boolean isRequired() {
        return isCombined() && timelineType.isSelectable() && !hasSearchQuery();
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
        if (!isSyncable()) {
            builder.append(", not syncable");
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

    public String positionsToString() {
        StringBuilder builder = new StringBuilder();

        builder.append("TimelinePositions{");
        if (youngestSyncedDate > 0) {
            builder.append("synced at " + new Date(youngestSyncedDate).toString());
            builder.append(", pos:" + getYoungestPosition());
        }
        if (oldestSyncedDate > 0) {
            builder.append("older synced at " + new Date(oldestSyncedDate).toString());
            builder.append(", pos:" + getOldestPosition());
        }
        builder.append('}');
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Timeline)) return false;

        Timeline timeline = (Timeline) o;

        if (timelineType != timeline.timelineType) return false;
        if (!origin.equals(timeline.origin)) return false;
        if (!myAccount.equals(timeline.myAccount)) return false;
        if (userId != timeline.userId) return false;
        return StringUtils.equalsNotEmpty(searchQuery, timeline.searchQuery);
    }

    @Override
    public int hashCode() {
        int result = timelineType.hashCode();
        result = 31 * result + origin.hashCode();
        result = 31 * result + myAccount.hashCode();
        result = 31 * result + (int) (userId ^ (userId >>> 32));
        if (!TextUtils.isEmpty(searchQuery)) {
            result = 31 * result + searchQuery.hashCode();
        }
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
        if (System.currentTimeMillis() - Math.max(getSyncSucceededDate(), getSyncFailedDate()) < MIN_RETRY_PERIOD_MS) {
            return false;
        }
        long syncFrequencyMs = myAccount.getEffectiveSyncFrequencySeconds() * 1000;
        // This correction is needed to take into account that we remembered time, when sync ended,
        // and not time, when Android initiated it.
        long correctionForExecutionTime = syncFrequencyMs / 10;
        long passedMs = System.currentTimeMillis() - getSyncSucceededDate();
        boolean blnOut = passedMs > syncFrequencyMs - correctionForExecutionTime;

        if (blnOut && MyLog.isVerboseEnabled()) {
            MyLog.v(this, "It's time to auto update " + this +
                    ". " +
                    java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(passedMs) +
                    " minutes passed.");
        }
        return blnOut;
    }

    public void forgetPositionsAndDates() {
        if (!TextUtils.isEmpty(youngestPosition)) {
            youngestPosition = "";
            changed = true;
        }
        if (youngestItemDate > 0) {
            youngestItemDate = 0;
            changed = true;
        }
        if (youngestSyncedDate > 0) {
            youngestSyncedDate = 0;
            changed = true;
        }

        if (!TextUtils.isEmpty(oldestPosition)) {
            oldestPosition = "";
            changed = true;
        }
        if (oldestItemDate > 0) {
            oldestItemDate = 0;
            changed = true;
        }
        if (oldestSyncedDate > 0) {
            oldestSyncedDate = 0;
            changed = true;
        }

        setSyncSucceededDate(0);
        if (syncFailedDate > 0) {
            syncFailedDate = 0;
            changed = true;
        }
    }

    public void onNewMsg(long newDate, String newPosition) {
        if (newDate <= 0 || TextUtils.isEmpty(newPosition)) {
            return;
        }
        if (youngestItemDate < newDate ||
                ( youngestItemDate == newDate && StringUtils.isNewFilledValue(youngestPosition, newPosition))) {
            youngestItemDate = newDate;
            youngestPosition = newPosition;
            changed = true;
        }
        if (oldestItemDate == 0 || oldestItemDate > newDate ||
                (oldestItemDate == newDate && StringUtils.isNewFilledValue(oldestPosition, newPosition))) {
            oldestItemDate = newDate;
            oldestPosition = newPosition;
            changed = true;
        }
    }

    public String getYoungestPosition() {
        return youngestPosition;
    }

    public long getYoungestItemDate() {
        return youngestItemDate;
    }

    public long getYoungestSyncedDate() {
        return youngestSyncedDate;
    }

    public void setYoungestSyncedDate(long newDate) {
        if (youngestSyncedDate < newDate) {
            youngestSyncedDate = newDate;
            changed = true;
        }
    }

    public long getOldestItemDate() {
        return oldestItemDate;
    }

    public String getOldestPosition() {
        return oldestPosition;
    }

    public long getOldestSyncedDate() {
        return oldestSyncedDate;
    }

    public void setOldestSyncedDate(long newDate) {
        if (oldestSyncedDate < newDate) {
            oldestSyncedDate = newDate;
            changed = true;
        }
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
        if (this.isSyncedAutomatically != isSyncedAutomatically && isSyncableAutomatically()) {
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
        if (result.getDownloadedCount() > 0) {
            downloadedItemsCount += result.getDownloadedCount();
            downloadedItemsCountTotal += result.getDownloadedCount();
        }
        changed = true;
    }

    public long getSyncSucceededDate() {
        return syncSucceededDate;
    }

    public void setSyncSucceededDate(long syncSucceededDate) {
        if(this.syncSucceededDate != syncSucceededDate) {
            this.syncSucceededDate = syncSucceededDate;
            changed = true;
        }
    }

    public boolean isSynableSomehow() {
        return isSyncable || isSyncableForOrigins() || isSyncableForAccounts();
    }

    public boolean isSyncable() {
        return isSyncable;
    }

    public boolean isSyncableAutomatically() {
        return isSyncableAutomatically;
    }

    public boolean isSyncableForAccounts() {
        return isSyncableForAccounts;
    }

    public boolean isSyncableForOrigins() {
        return isSyncableForOrigins;
    }

    public long getDownloadedItemsCount(boolean isTotal) {
        return isTotal ? downloadedItemsCountTotal : downloadedItemsCount;
    }

    public long getNewItemsCount(boolean isTotal) {
        return isTotal ? newItemsCountTotal : newItemsCount;
    }

    public long getSyncedTimesCount(boolean isTotal) {
        return isTotal ? syncedTimesCountTotal : syncedTimesCount;
    }

    public long getSyncFailedDate() {
        return syncFailedDate;
    }

    public long getSyncFailedTimesCount(boolean isTotal) {
        return isTotal ? syncFailedTimesCountTotal : syncFailedTimesCount;
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

    public void resetCounters(boolean all) {
        if (all) {
            syncFailedTimesCountTotal = 0;
            syncedTimesCountTotal = 0;
            downloadedItemsCountTotal = 0;
            newItemsCountTotal = 0;
        }
        errorMessage = "";
        syncFailedTimesCount = 0;
        syncedTimesCount = 0;
        downloadedItemsCount = 0;
        newItemsCount = 0;
        countSince = System.currentTimeMillis();
        changed = true;
    }

    public long getCountSince() {
        return countSince;
    }
}
