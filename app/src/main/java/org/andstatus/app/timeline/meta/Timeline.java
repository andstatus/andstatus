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

package org.andstatus.app.timeline.meta;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.ContentValuesUtils;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.ParsedUri;
import org.andstatus.app.data.SqlWhere;
import org.andstatus.app.database.table.CommandTable;
import org.andstatus.app.database.table.TimelineTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.timeline.ListScope;
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

/**
 * @author yvolk@yurivolkov.com
 */
public class Timeline implements Comparable<Timeline>, IsEmpty {
    public static final Timeline EMPTY = new Timeline();
    private static final long MIN_RETRY_PERIOD_MS = TimeUnit.SECONDS.toMillis(30);
    public static final String TIMELINE_CLICK_HOST = "timeline.app.andstatus.org";
    public final MyContext myContext;
    private volatile long id;

    private final TimelineType timelineType;
    /** "Authenticated User" used to retrieve/post to... this Timeline */
    public final MyAccount myAccountToSync;
    /** An Actor as a parameter of this timeline.
     * This may be the same as the Authenticated User ({@link #myAccountToSync})
     * or some other User e.g. to get a list of messages by some other person/user of the Social Network
     */
    public final Actor actor;
    /** The Social Network of this timeline. Some timelines don't depend on
     * an Authenticated User ({@link #myAccountToSync}), e.g. {@link TimelineType#PUBLIC} - this
     * timeline may be fetched by any authenticated user of this Social Network */
    private final Origin origin;
    /** Pre-fetched string to be used to present in UI */
    private String actorInTimeline = "";
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
    private final AtomicLong syncSucceededDate = new AtomicLong();
    /** When last sync error occurred */
    private final AtomicLong syncFailedDate = new AtomicLong();
    /** Error message at {@link #syncFailedDate} */
    private volatile String errorMessage = "";

    /** Number of successful sync operations: "Synced {@link #syncedTimesCount} times" */
    private final AtomicLong syncedTimesCount = new AtomicLong();
    /** Number of failed sync operations */
    private final AtomicLong syncFailedTimesCount = new AtomicLong();
    private final AtomicLong downloadedItemsCount = new AtomicLong();
    private final AtomicLong newItemsCount = new AtomicLong();
    private final AtomicLong countSince = new AtomicLong(System.currentTimeMillis());

    /** Accumulated numbers for statistics. They are reset by a user's request */
    private final AtomicLong syncedTimesCountTotal = new AtomicLong();
    private final AtomicLong syncFailedTimesCountTotal = new AtomicLong();
    private final AtomicLong downloadedItemsCountTotal = new AtomicLong();
    private final AtomicLong newItemsCountTotal = new AtomicLong();

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

    private volatile boolean changed = true;
    private volatile long lastChangedDate = 0;

    public static Timeline fromCursor(MyContext myContext, Cursor cursor) {
        Timeline timeline = new Timeline(
                myContext,
                DbUtils.getLong(cursor, TimelineTable._ID),
                TimelineType.load(DbUtils.getString(cursor, TimelineTable.TIMELINE_TYPE)),
                Actor.load(myContext, DbUtils.getLong(cursor, TimelineTable.ACTOR_ID)),
                myContext.origins().fromId(DbUtils.getLong(cursor, TimelineTable.ORIGIN_ID)),
                DbUtils.getString(cursor, TimelineTable.SEARCH_QUERY),
                DbUtils.getLong(cursor, TimelineTable.SELECTOR_ORDER));

        timeline.changed = false;
        timeline.actorInTimeline = DbUtils.getString(cursor, TimelineTable.ACTOR_IN_TIMELINE);
        timeline.setSyncedAutomatically(DbUtils.getBoolean(cursor, TimelineTable.IS_SYNCED_AUTOMATICALLY));
        timeline.isDisplayedInSelector = DisplayedInSelector.load(DbUtils.getString(cursor, TimelineTable.DISPLAYED_IN_SELECTOR));

        timeline.syncSucceededDate.set(DbUtils.getLong(cursor, TimelineTable.SYNC_SUCCEEDED_DATE));
        timeline.syncFailedDate.set(DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_DATE));
        timeline.errorMessage = DbUtils.getString(cursor, TimelineTable.ERROR_MESSAGE);

        timeline.syncedTimesCount.set(DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT));
        timeline.syncFailedTimesCount.set(DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT));
        timeline.downloadedItemsCount.set(DbUtils.getLong(cursor, TimelineTable.DOWNLOADED_ITEMS_COUNT));
        timeline.newItemsCount.set(DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT));
        timeline.countSince.set(DbUtils.getLong(cursor, TimelineTable.COUNT_SINCE));
        timeline.syncedTimesCountTotal.set(DbUtils.getLong(cursor, TimelineTable.SYNCED_TIMES_COUNT_TOTAL));
        timeline.syncFailedTimesCountTotal.set(DbUtils.getLong(cursor, TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL));
        timeline.downloadedItemsCountTotal.set(DbUtils.getLong(cursor, TimelineTable.DOWNLOADED_ITEMS_COUNT_TOTAL));
        timeline.newItemsCountTotal.set(DbUtils.getLong(cursor, TimelineTable.NEW_ITEMS_COUNT_TOTAL));

        timeline.youngestPosition = DbUtils.getString(cursor, TimelineTable.YOUNGEST_POSITION);
        timeline.youngestItemDate = DbUtils.getLong(cursor, TimelineTable.YOUNGEST_ITEM_DATE);
        timeline.youngestSyncedDate = DbUtils.getLong(cursor, TimelineTable.YOUNGEST_SYNCED_DATE);
        timeline.oldestPosition = DbUtils.getString(cursor, TimelineTable.OLDEST_POSITION);
        timeline.oldestItemDate = DbUtils.getLong(cursor, TimelineTable.OLDEST_ITEM_DATE);
        timeline.oldestSyncedDate = DbUtils.getLong(cursor, TimelineTable.OLDEST_SYNCED_DATE);

        timeline.visibleItemId = DbUtils.getLong(cursor, TimelineTable.VISIBLE_ITEM_ID);
        timeline.visibleY = DbUtils.getInt(cursor, TimelineTable.VISIBLE_Y);
        timeline.visibleOldestDate = DbUtils.getLong(cursor, TimelineTable.VISIBLE_OLDEST_DATE);

        timeline.lastChangedDate = DbUtils.getLong(cursor, TimelineTable.LAST_CHANGED_DATE);

        return timeline;
    }

    public static Timeline fromCommandCursor(MyContext myContext, Cursor cursor) {
        return myContext.timelines().get(
                DbUtils.getLong(cursor, CommandTable.TIMELINE_ID),
                TimelineType.load(DbUtils.getString(cursor, CommandTable.TIMELINE_TYPE)),
                Actor.load(myContext, DbUtils.getLong(cursor, CommandTable.ACTOR_ID)),
                myContext.origins().fromId(DbUtils.getLong(cursor, CommandTable.ORIGIN_ID)),
                DbUtils.getString(cursor, CommandTable.SEARCH_QUERY)
        );
    }

    public static Timeline fromBundle(MyContext myContext, Bundle bundle) {
        if (bundle == null) return EMPTY;
        Timeline timeline = myContext.timelines().fromId(bundle.getLong(IntentExtra.TIMELINE_ID.key));
        return timeline.nonEmpty()
                ? timeline
                : myContext.timelines().get(
                    TimelineType.load(bundle.getString(IntentExtra.TIMELINE_TYPE.key)),
                    Actor.load(myContext, bundle.getLong(IntentExtra.ACTOR_ID.key)),
                    myContext.origins().fromId(BundleUtils.fromBundle(bundle, IntentExtra.ORIGIN_ID)),
                    BundleUtils.getString(bundle, IntentExtra.SEARCH_QUERY));
    }

    public static Timeline fromParsedUri(MyContext myContext, ParsedUri parsedUri, String searchQueryIn) {
        Timeline timeline = myContext.timelines().get(
                parsedUri.getTimelineType(),
                Actor.load(myContext, parsedUri.getActorId()),
                parsedUri.getOrigin(myContext),
                StringUtil.isEmpty(searchQueryIn) ? parsedUri.getSearchQuery() : searchQueryIn
        );
        if (timeline.getTimelineType() == TimelineType.UNKNOWN && parsedUri.getActorListType() == ActorListType.UNKNOWN) {
            MyLog.w(Timeline.class, "fromParsedUri; uri:" + parsedUri.getUri() + "; " + timeline);
        }
        return timeline;
    }

    private Timeline() {
        myContext = MyContext.EMPTY;
        timelineType = TimelineType.UNKNOWN;
        myAccountToSync = MyAccount.EMPTY;
        actor = Actor.EMPTY;
        origin = Origin.EMPTY;
        searchQuery = "";
        isCombined = calcIsCombined(timelineType, origin);
        isSyncable = false;
        isSyncableAutomatically = false;
        isSyncableForAccounts = false;
        isSyncableForOrigins = false;
    }

    Timeline(MyContext myContext, long id, @NonNull TimelineType timelineType,
             @NonNull Actor actor, @NonNull Origin origin, String searchQuery, long selectorOrder) {
        Objects.requireNonNull(timelineType);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(origin);
        this.myContext = myContext;
        this.id = id;
        this.actor = fixedActor(timelineType, actor);
        this.origin = fixedOrigin(timelineType, origin);
        this.myAccountToSync = calcAccountToSync(myContext, timelineType, this.origin, this.actor);
        this.searchQuery = StringUtil.optNotEmpty(searchQuery).orElse("");
        this.isCombined = calcIsCombined(timelineType, this.origin);
        this.timelineType = fixedTimelineType(timelineType);
        this.isSyncable = calcIsSyncable(myAccountToSync);
        this.isSyncableAutomatically = this.isSyncable && myAccountToSync.isSyncedAutomatically();
        this.isSyncableForAccounts = calcIsSyncableForAccounts(myContext);
        this.isSyncableForOrigins = calcIsSyncableForOrigins(myContext);
        this.selectorOrder = selectorOrder;
    }

    long getDefaultSelectorOrder() {
        return (timelineType.ordinal() + 1L) * 2 + (isCombined ? 1 : 0);
    }

    private boolean calcIsSyncable(MyAccount myAccountToSync) {
        return !isCombined() && timelineType.isSyncable()
                && myAccountToSync.isValidAndSucceeded()
                && myAccountToSync.getOrigin().getOriginType().isTimelineTypeSyncable(timelineType);
    }

    private boolean calcIsSyncableForAccounts(MyContext myContext) {
        return isCombined &&
                timelineType.isSyncable() && timelineType.canBeCombinedForMyAccounts() &&
                myContext.accounts().getFirstSucceeded().isValidAndSucceeded();
    }

    private boolean calcIsSyncableForOrigins(MyContext myContext) {
        return isCombined &&
                timelineType.isSyncable() && timelineType.canBeCombinedForOrigins() &&
                myContext.accounts().getFirstSucceeded().isValidAndSucceeded();
    }

    private boolean calcIsCombined(TimelineType timelineType, Origin origin) {
        return timelineType.isAtOrigin() ? origin.isEmpty() : actor.isEmpty();
    }

    @NonNull
    private MyAccount calcAccountToSync(MyContext myContext, TimelineType timelineType, Origin origin, Actor actor) {
        return timelineType.isAtOrigin() && origin.nonEmpty()
                ? myContext.accounts().getFirstSucceededForOrigin(origin)
                : myContext.accounts().toSyncThatActor(actor);
    }

    private Actor fixedActor(TimelineType timelineType, Actor actor) {
        return timelineType.isForUser() ? actor : Actor.EMPTY;
    }

    @NonNull
    private Origin fixedOrigin(TimelineType timelineType, Origin origin) {
        return timelineType.isAtOrigin() ? origin : Origin.EMPTY;
    }

    private TimelineType fixedTimelineType(TimelineType timelineType) {
        return isCombined || (timelineType.isAtOrigin() ? origin.isValid() : actor.nonEmpty())
                ? timelineTypeFixEverything(timelineType)
                : TimelineType.UNKNOWN;
    }

    private TimelineType timelineTypeFixEverything(TimelineType timelineType) {
        switch (timelineType) {
            case EVERYTHING:
            case SEARCH:
                return hasSearchQuery() ? TimelineType.SEARCH : TimelineType.EVERYTHING;
            default:
                return timelineType;
        }
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
        values.put(TimelineTable.ACTOR_ID, actor.actorId);
        values.put(TimelineTable.ACTOR_IN_TIMELINE, actorInTimeline);
        values.put(TimelineTable.ORIGIN_ID, origin.getId());
        values.put(TimelineTable.SEARCH_QUERY, searchQuery);

        values.put(TimelineTable.IS_SYNCED_AUTOMATICALLY, isSyncedAutomatically);
        values.put(TimelineTable.DISPLAYED_IN_SELECTOR, isDisplayedInSelector.save());
        values.put(TimelineTable.SELECTOR_ORDER, selectorOrder);

        values.put(TimelineTable.SYNC_SUCCEEDED_DATE, syncSucceededDate.get());
        values.put(TimelineTable.SYNC_FAILED_DATE, syncFailedDate.get());
        values.put(TimelineTable.ERROR_MESSAGE, errorMessage);

        values.put(TimelineTable.SYNCED_TIMES_COUNT, syncedTimesCount.get());
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT, syncFailedTimesCount.get());
        values.put(TimelineTable.DOWNLOADED_ITEMS_COUNT, downloadedItemsCount.get());
        values.put(TimelineTable.NEW_ITEMS_COUNT, newItemsCount.get());
        values.put(TimelineTable.COUNT_SINCE, countSince.get());
        values.put(TimelineTable.SYNCED_TIMES_COUNT_TOTAL, syncedTimesCountTotal.get());
        values.put(TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL, syncFailedTimesCountTotal.get());
        values.put(TimelineTable.DOWNLOADED_ITEMS_COUNT_TOTAL, downloadedItemsCountTotal.get());
        values.put(TimelineTable.NEW_ITEMS_COUNT_TOTAL, newItemsCountTotal.get());

        values.put(TimelineTable.YOUNGEST_POSITION, youngestPosition);
        values.put(TimelineTable.YOUNGEST_ITEM_DATE, youngestItemDate);
        values.put(TimelineTable.YOUNGEST_SYNCED_DATE, youngestSyncedDate);
        values.put(TimelineTable.OLDEST_POSITION, oldestPosition);
        values.put(TimelineTable.OLDEST_ITEM_DATE, oldestItemDate);
        values.put(TimelineTable.OLDEST_SYNCED_DATE, oldestSyncedDate);

        values.put(TimelineTable.VISIBLE_ITEM_ID, visibleItemId);
        values.put(TimelineTable.VISIBLE_Y, visibleY);
        values.put(TimelineTable.VISIBLE_OLDEST_DATE, visibleOldestDate);

        if (lastChangedDate > 0) values.put(TimelineTable.LAST_CHANGED_DATE, lastChangedDate);
    }

    public void toCommandContentValues(ContentValues values) {
        values.put(CommandTable.TIMELINE_ID, id);
        values.put(CommandTable.TIMELINE_TYPE, timelineType.save());
        values.put(CommandTable.ACTOR_ID, actor.actorId);
        values.put(CommandTable.ORIGIN_ID, origin.getId());
        values.put(CommandTable.SEARCH_QUERY, searchQuery);
    }

    public Timeline fromSearch(MyContext myContext, boolean globalSearch) {
        return globalSearch
                ? myContext.timelines().get(TimelineType.SEARCH, actor, origin, searchQuery)
                : this;
    }

    public Timeline fromIsCombined(MyContext myContext, boolean isCombinedNew) {
        if (isCombined == isCombinedNew || (
                !isCombined && timelineType.isForUser() && !timelineType.isAtOrigin() &&
                    actor.user.isMyUser() != TriState.TRUE
        )) return this;
        return myContext.timelines().get(timelineType,
                isCombinedNew ? Actor.EMPTY : myContext.accounts().getCurrentAccount().getActor(),
                isCombinedNew ? Origin.EMPTY : myContext.accounts().getCurrentAccount().getOrigin(),
                searchQuery);
    }

    public Timeline fromMyAccount(MyContext myContext, MyAccount myAccountNew) {
        if (isCombined() || myAccountToSync.equals(myAccountNew)
                || (timelineType.isForUser() && !timelineType.isAtOrigin() && actor.user.isMyUser() != TriState.TRUE)) return this;
        return myContext.timelines().get(
                timelineType,
                myAccountNew.getActor(),
                myAccountNew.getOrigin(), searchQuery);
    }

    @Override
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
    public Origin preferredOrigin() {
        return origin.nonEmpty()
                ? origin
                : actor.nonEmpty()
                    ? actor.toHomeOrigin().origin
                    : myContext.accounts().getCurrentAccount().getOrigin();
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
            setChanged();
        }
    }

    public long getSelectorOrder() {
        return selectorOrder;
    }

    public void setSelectorOrder(long selectorOrder) {
        if (this.selectorOrder != selectorOrder) {
            setChanged();
            this.selectorOrder = selectorOrder;
        }
    }

    @NonNull
    public Timeline save(MyContext myContext) {
        if (MyAsyncTask.isUiThread()) return this;

        if (needToLoadActorInTimeline()) {
            setChanged();
        }
        if (timelineType.isPersistable() && (id == 0 || changed) && myContext.isReady()) {
            boolean isNew = id == 0;
            if (isNew) {
                long duplicatedId = findDuplicateInDatabase(myContext);
                if (duplicatedId != 0) {
                    MyLog.i(this, "Found duplicating timeline, id=" + duplicatedId + " for " + this);
                    return myContext.timelines().fromId(duplicatedId);
                }
                if (isAddedByDefault()) {
                    setDisplayedInSelector(DisplayedInSelector.IN_CONTEXT);
                    setSyncedAutomatically(getTimelineType().isSyncedAutomaticallyByDefault());
                }
            }
            if (selectorOrder == 0) {
                this.selectorOrder = getDefaultSelectorOrder();
            }
            saveInternal(myContext);
            if (isNew && id != 0) {
                return myContext.timelines().fromId(id);
            }
        }
        return this;
    }

    private long findDuplicateInDatabase(MyContext myContext) {
        SqlWhere where = new SqlWhere();
        where.append(TimelineTable.TIMELINE_TYPE + "='" + timelineType.save() + "'");
        where.append(TimelineTable.ORIGIN_ID + "=" + origin.getId());
        where.append(TimelineTable.ACTOR_ID + "=" + actor.actorId);
        where.append(TimelineTable.SEARCH_QUERY + "='" + searchQuery + "'");
        return MyQuery.conditionToLongColumnValue(
                myContext.getDatabase(),
                "findDuplicateInDatabase",
                TimelineTable.TABLE_NAME,
                TimelineTable._ID,
                where.getCondition());
    }

    private long saveInternal(MyContext myContext) {
        if (needToLoadActorInTimeline()) {
            actorInTimeline = MyQuery.actorIdToName(myContext, actor.actorId, MyPreferences.getActorInTimeline());
        }
        ContentValues contentValues = new ContentValues();
        toContentValues(contentValues);
        if (myContext.isTestRun()) {
            MyLog.v(this, () -> "Saving " + this);
        }
        if (getId() == 0) {
            DbUtils.addRowWithRetry(myContext, TimelineTable.TABLE_NAME, contentValues, 3)
            .onSuccess(idAdded -> {
                id = idAdded;
                changed = false;
            });
        } else {
            DbUtils.updateRowWithRetry(myContext, TimelineTable.TABLE_NAME, getId(), contentValues, 3)
            .onSuccess( o -> changed = false);
        }
        return getId();
    }

    private boolean needToLoadActorInTimeline() {
        return actor.nonEmpty()
                && StringUtil.isEmptyOrTemp(actorInTimeline)
                && actor.user.isMyUser().untrue;
    }

    public void delete(MyContext myContext) {
        if (isRequired() && myContext.timelines().stream().noneMatch(this::duplicates)) {
            MyLog.d(this, "Cannot delete required timeline: " + this);
            return;
        }
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.d(this, "delete; Database is unavailable");
        } else {
            String sql = "DELETE FROM " + TimelineTable.TABLE_NAME + " WHERE _ID=" + getId();
            db.execSQL(sql);
            MyLog.v(this, () -> "Timeline deleted: " + this);
        }
    }

    public boolean isAddedByDefault() {
        if (isRequired()) return true;
        if (isCombined || !isValid() || hasSearchQuery()) return false;
        if (timelineType.isAtOrigin()) {
            return TimelineType.getDefaultOriginTimelineTypes().contains(timelineType)
                    && (origin.getOriginType().isTimelineTypeSyncable(timelineType)
                    || timelineType.equals(TimelineType.EVERYTHING));
        } else {
            return actor.user.isMyUser().isTrue && actor.getDefaultMyAccountTimelineTypes().contains(timelineType);
        }
    }

    /** Required timeline cannot be deleted */
    public boolean isRequired() {
        return isCombined() && timelineType.isCombinedRequired() && !hasSearchQuery();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Timeline{");
        if (timelineType.isAtOrigin()) {
            builder.append(origin.isValid() ? origin.getName() : "(all origins)");
        }
        if (timelineType.isForUser()) {
            if (actor.isEmpty()) {
                builder.append("(all accounts)");
            } else if (myAccountToSync.isValid()) {
                builder.append(myAccountToSync.getAccountName());
                if (!myAccountToSync.getOrigin().equals(origin) && origin.isValid()) {
                    builder.append(", origin:" + origin.getName());
                }
            } else {
                builder.append(actor.user);
            }
        }
        if (timelineType != TimelineType.UNKNOWN) {
            builder.append(", type:" + timelineType.save());
        }
        if (StringUtil.nonEmpty(actorInTimeline)) {
            builder.append(", actor:'" + actorInTimeline + "'");
        } else if (actor.nonEmpty()) {
            builder.append(", actor:" + actor);
        }
        if (hasSearchQuery()) {
            builder.append(" search:'" + getSearchQuery() + "'");
        }
        if ( id != 0) {
            builder.append(", id:" + id);
        }
        if (!isSyncable()) {
            builder.append(", not syncable");
        }
        builder.append('}');
        return builder.toString();
    }

    public String positionsToString() {
        StringBuilder builder = new StringBuilder();

        builder.append("TimelinePositions{");
        if (youngestSyncedDate > SOME_TIME_AGO) {
            builder.append("synced at " + new Date(youngestSyncedDate).toString());
            builder.append(", pos:" + getYoungestPosition());
        }
        if (oldestSyncedDate > SOME_TIME_AGO) {
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

        Timeline that = (Timeline) o;

        if (timelineType != that.timelineType) return false;
        if (id != 0 || that.id != 0) {
            return id == that.id;
        }
        if (!origin.equals(that.origin)) return false;
        if (!actor.equals(that.actor)) return false;
        return StringUtil.equalsNotEmpty(searchQuery, that.searchQuery);
    }

    public boolean duplicates(Timeline that) {
        if (equals(that)) return false;
        if (id > 0 && id < that.id) return false;
        if (timelineType != that.timelineType) return false;
        if (!origin.equals(that.origin)) return false;
        if (!actor.isSame(that.actor)) return false;
        return StringUtil.equalsNotEmpty(searchQuery, that.searchQuery);
    }

    @Override
    public int hashCode() {
        int result = timelineType.hashCode();
        if (id != 0) result = 31 * result + Long.hashCode(id);
        result = 31 * result + origin.hashCode();
        result = 31 * result + actor.hashCode();
        if (!StringUtil.isEmpty(searchQuery)) result = 31 * result + searchQuery.hashCode();
        return result;
    }

    public long getActorId() {
        return actor.actorId;
    }

    public boolean hasSearchQuery() {
        return !StringUtil.isEmpty(getSearchQuery());
    }

    @NonNull
    public String getSearchQuery() {
        return searchQuery;
    }

    public void toBundle(Bundle bundle) {
        BundleUtils.putNotZero(bundle, IntentExtra.TIMELINE_ID, id);
        if (timelineType != TimelineType.UNKNOWN) {
            bundle.putString(IntentExtra.TIMELINE_TYPE.key, timelineType.save());
        }
        BundleUtils.putNotZero(bundle, IntentExtra.ORIGIN_ID, origin.getId());
        BundleUtils.putNotZero(bundle, IntentExtra.ACTOR_ID, actor.actorId);
        BundleUtils.putNotEmpty(bundle, IntentExtra.SEARCH_QUERY, searchQuery);
    }

    /**
     * @return true if it's time to auto update this timeline
     */
    public boolean isTimeToAutoSync() {
        if (System.currentTimeMillis() - getLastSyncedDate() < MIN_RETRY_PERIOD_MS) {
            return false;
        }
        long syncFrequencyMs = myAccountToSync.getEffectiveSyncFrequencyMillis();
        // This correction needs to take into account
        // that we stored time when sync ended, and not when Android initiated the sync.
        long correctionForExecutionTime = syncFrequencyMs / 10;
        long passedMs = System.currentTimeMillis() - getLastSyncedDate();
        boolean blnOut = passedMs > syncFrequencyMs - correctionForExecutionTime;
        MyLog.v(this, () -> "It's time to auto update " + this +
                ". " +
                java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(passedMs) +
                " minutes passed.");
        return blnOut;
    }

    public void forgetPositionsAndDates() {
        if (!StringUtil.isEmpty(youngestPosition)) {
            youngestPosition = "";
            setChanged();
        }
        if (youngestItemDate > 0) {
            youngestItemDate = 0;
            setChanged();
        }
        if (youngestSyncedDate > 0) {
            youngestSyncedDate = 0;
            setChanged();
        }

        if (!StringUtil.isEmpty(oldestPosition)) {
            oldestPosition = "";
            setChanged();
        }
        if (oldestItemDate > 0) {
            oldestItemDate = 0;
            setChanged();
        }
        if (oldestSyncedDate > 0) {
            oldestSyncedDate = 0;
            setChanged();
        }

        setSyncSucceededDate(0);
        if (syncFailedDate.get() > 0) {
            syncFailedDate.set(0);
            setChanged();
        }
    }

    public void onNewMsg(long newDate, String youngerPosition, String olderPosition) {
        if (newDate <= SOME_TIME_AGO) return;

        if (StringUtil.nonEmpty(youngerPosition) && (youngestItemDate < newDate ||
                ( youngestItemDate == newDate && StringUtil.isNewFilledValue(youngestPosition, youngerPosition)))) {
            youngestItemDate = newDate;
            youngestPosition = youngerPosition;
            setChanged();
        }
        if (StringUtil.nonEmpty(olderPosition) && (oldestItemDate <= SOME_TIME_AGO || oldestItemDate > newDate ||
                (oldestItemDate == newDate && StringUtil.isNewFilledValue(oldestPosition, olderPosition)))) {
            oldestItemDate = newDate;
            oldestPosition = olderPosition;
            setChanged();
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
        if (newDate > SOME_TIME_AGO && youngestSyncedDate < newDate) {
            youngestSyncedDate = newDate;
            setChanged();
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
        if (newDate > SOME_TIME_AGO && (oldestSyncedDate <= SOME_TIME_AGO || oldestSyncedDate > newDate)) {
            oldestSyncedDate = newDate;
            setChanged();
        }
    }

    public long getVisibleItemId() {
        return visibleItemId;
    }

    public void setVisibleItemId(long visibleItemId) {
        if (this.visibleItemId != visibleItemId) {
            setChanged();
            this.visibleItemId = visibleItemId;
        }
    }

    public int getVisibleY() {
        return visibleY;
    }

    public void setVisibleY(int visibleY) {
        if (this.visibleY != visibleY) {
            setChanged();
            this.visibleY = visibleY;
        }
    }

    public long getVisibleOldestDate() {
        return visibleOldestDate;
    }

    public void setVisibleOldestDate(long visibleOldestDate) {
        if (this.visibleOldestDate != visibleOldestDate) {
            setChanged();
            this.visibleOldestDate = visibleOldestDate;
        }
    }

    public boolean isSyncedAutomatically() {
        return isSyncedAutomatically;
    }

    public void setSyncedAutomatically(boolean isSyncedAutomatically) {
        if (this.isSyncedAutomatically != isSyncedAutomatically && isSyncableAutomatically()) {
            this.isSyncedAutomatically = isSyncedAutomatically;
            setChanged();
        }
    }

    public boolean isChanged() {
        return changed;
    }

    public Timeline cloneForAccount(MyContext myContext, MyAccount ma) {
        return myContext.timelines().get(0, getTimelineType(), ma.getActor(), Origin.EMPTY, getSearchQuery());
    }

    public Timeline cloneForOrigin(MyContext myContext, Origin origin) {
        return myContext.timelines().get(0, getTimelineType(), Actor.EMPTY, origin, getSearchQuery());
    }

    @NonNull
    static Timeline fromId(MyContext myContext, long id) {
        return id == 0
                ? Timeline.EMPTY
                : MyQuery.get(myContext,
                "SELECT * FROM " + TimelineTable.TABLE_NAME + " WHERE " + TimelineTable._ID + "=" + id,
                cursor -> Timeline.fromCursor(myContext, cursor)).stream().findFirst().orElse(EMPTY);
    }

    public void onSyncEnded(MyContext myContext, CommandResult result) {
        onSyncEnded(result).save(myContext);
        myContext.timelines().stream()
                .filter(Timeline::isSyncable)
                .filter(this::isSyncedSimultaneously)
                .forEach(timeline -> timeline.onSyncedSimultaneously(this).save(myContext));
    }

    private Timeline onSyncedSimultaneously(Timeline other) {
        if (setIfLess(syncFailedDate, other.syncFailedDate)) {
            errorMessage = other.errorMessage;
        }
        setIfLess(syncFailedTimesCount, other.syncFailedTimesCount);
        setIfLess(syncFailedTimesCountTotal, other.syncFailedTimesCountTotal);
        setIfLess(syncSucceededDate, other.syncSucceededDate);
        setIfLess(syncedTimesCount, other.syncedTimesCount);
        setIfLess(syncedTimesCountTotal, other.syncedTimesCountTotal);

        setIfLess(newItemsCount, other.newItemsCount);
        setIfLess(newItemsCountTotal, other.newItemsCountTotal);
        setIfLess(downloadedItemsCount, other.downloadedItemsCount);
        setIfLess(downloadedItemsCountTotal, other.downloadedItemsCountTotal);

        onNewMsg(other.youngestItemDate, other.youngestPosition, "");
        onNewMsg(other.oldestItemDate, "", other.oldestPosition);
        setYoungestSyncedDate(other.youngestSyncedDate);
        setOldestSyncedDate(other.oldestSyncedDate);

        return this;
    }

    private static boolean setIfLess(AtomicLong value, AtomicLong other) {
        if (value.get() < other.get()) {
            value.set(other.get());
            return true;
        }
        return false;
    }

    private boolean isSyncedSimultaneously(Timeline timeline) {
        return !this.equals(timeline)
            && !timeline.isCombined
            && getTimelineType().getConnectionApiRoutine() == timeline.getTimelineType().getConnectionApiRoutine()
            && searchQuery.equals(timeline.searchQuery)
            && myAccountToSync.equals(timeline.myAccountToSync)
            && actor.equals(timeline.actor)
            && origin.equals(timeline.origin);
    }

    private Timeline onSyncEnded(CommandResult result) {
        if (result.hasError()) {
            syncFailedDate.set(System.currentTimeMillis());
            if (!StringUtil.isEmpty(result.getMessage())) {
                errorMessage = result.getMessage();
            }
            syncFailedTimesCount.incrementAndGet();
            syncFailedTimesCountTotal.incrementAndGet();
        } else {
            syncSucceededDate.set(System.currentTimeMillis());
            syncedTimesCount.incrementAndGet();
            syncedTimesCountTotal.incrementAndGet();
        }
        if (result.getNewCount() > 0) {
            newItemsCount.addAndGet(result.getNewCount());
            newItemsCountTotal.addAndGet(result.getNewCount());
        }
        if (result.getDownloadedCount() > 0) {
            downloadedItemsCount.addAndGet(result.getDownloadedCount());
            downloadedItemsCountTotal.addAndGet(result.getDownloadedCount());
        }
        setChanged();
        return this;
    }

    public long getSyncSucceededDate() {
        return syncSucceededDate.get();
    }

    public void setSyncSucceededDate(long syncSucceededDate) {
        if(this.syncSucceededDate.get() != syncSucceededDate) {
            this.syncSucceededDate.set(syncSucceededDate);
            setChanged();
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

    public boolean isSyncedByOtherUser() {
        return actor.isEmpty() || myAccountToSync.getActor().notSameUser(actor);
    }

    public long getDownloadedItemsCount(boolean isTotal) {
        return isTotal ? downloadedItemsCountTotal.get() : downloadedItemsCount.get();
    }

    public long getNewItemsCount(boolean isTotal) {
        return isTotal ? newItemsCountTotal.get() : newItemsCount.get();
    }

    public long getSyncedTimesCount(boolean isTotal) {
        return isTotal ? syncedTimesCountTotal.get() : syncedTimesCount.get();
    }

    public long getSyncFailedDate() {
        return syncFailedDate.get();
    }

    public long getSyncFailedTimesCount(boolean isTotal) {
        return isTotal ? syncFailedTimesCountTotal.get() : syncFailedTimesCount.get();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getLastSyncedDate() {
        return Long.max(getSyncSucceededDate(), getSyncFailedDate());
    }

    @NonNull
    public String getActorInTimeline() {
        if (StringUtil.isEmpty(actorInTimeline)) {
            if (needToLoadActorInTimeline()) {
                return "...";
            } else {
                return actor.user.getKnownAs();
            }
        } else {
            return actorInTimeline;
        }
    }

    public void resetCounters(boolean all) {
        if (all) {
            syncFailedTimesCountTotal.set(0);
            syncedTimesCountTotal.set(0);
            downloadedItemsCountTotal.set(0);
            newItemsCountTotal.set(0);
        }
        errorMessage = "";
        syncFailedTimesCount.set(0);
        syncedTimesCount.set(0);
        downloadedItemsCount.set(0);
        newItemsCount.set(0);
        countSince.set(System.currentTimeMillis());
        changed = true;
    }

    public long getCountSince() {
        return countSince.get();
    }

    public Uri getUri() {
        return MatchedUri.getTimelineUri(this);
    }

    public Uri getClickUri() {
        return Uri.parse("content://" + TIMELINE_CLICK_HOST + getUri().getEncodedPath());
    }

    private void setChanged() {
        changed = true;
        lastChangedDate = System.currentTimeMillis();
    }

    public long getLastChangedDate() {
        return lastChangedDate;
    }

    public boolean match(boolean isForSelector, TriState isTimelineCombined, @NonNull TimelineType timelineType,
                         @NonNull Actor actor, @NonNull Origin origin) {
        if (isForSelector && isDisplayedInSelector() == DisplayedInSelector.ALWAYS) {
            return true;
        } else if (isForSelector && isDisplayedInSelector() == DisplayedInSelector.NEVER) {
            return false;
        } else if (timelineType != TimelineType.UNKNOWN && timelineType != getTimelineType()) {
            return false;
        } else if (isTimelineCombined == TriState.TRUE) {
            return isCombined();
        } else if (isTimelineCombined == TriState.FALSE && isCombined()) {
            return false;
        } else if (timelineType == TimelineType.UNKNOWN || timelineType.scope == ListScope.ACTOR_AT_ORIGIN) {
            return (actor.actorId == 0 || actor.actorId == getActorId())
                    && (origin.isEmpty() || origin.equals(getOrigin())) ;
        } else if (timelineType.isAtOrigin()) {
            return origin.isEmpty() || origin.equals(getOrigin());
        }
        return actor.actorId == 0 || actor.actorId == getActorId();
    }

    public boolean hasActorProfile() {
        return !isCombined && actor.nonEmpty() && timelineType.hasActorProfile();
    }

    public Timeline orElse(Timeline aDefault) {
        return isEmpty() ? aDefault : this;
    }
}
