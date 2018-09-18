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
import android.support.annotation.NonNull;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
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
import org.andstatus.app.util.BundleUtils;
import org.andstatus.app.util.CollectionsUtil;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author yvolk@yurivolkov.com
 */
public class Timeline implements Comparable<Timeline>, IsEmpty {
    public static final Timeline EMPTY = new Timeline();
    private static final long MIN_RETRY_PERIOD_MS = TimeUnit.SECONDS.toMillis(30);
    public static final String TIMELINE_CLICK_HOST = "timeline.app.andstatus.org";
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

    private volatile boolean changed = true;
    private volatile long lastChangedDate = 0;

    public static Timeline getTimeline(@NonNull TimelineType timelineType, long actorId, @NonNull Origin origin) {
        return MyContextHolder.get().timelines().get(timelineType, actorId, origin, "");
    }

    public static Timeline fromCursor(MyContext myContext, Cursor cursor) {
        Timeline timeline = new Timeline(
                myContext,
                DbUtils.getLong(cursor, TimelineTable._ID),
                TimelineType.load(DbUtils.getString(cursor, TimelineTable.TIMELINE_TYPE)),
                DbUtils.getLong(cursor, TimelineTable.ACTOR_ID),
                myContext.origins().fromId(DbUtils.getLong(cursor, TimelineTable.ORIGIN_ID)),
                DbUtils.getString(cursor, TimelineTable.SEARCH_QUERY));

        timeline.changed = false;
        timeline.actorInTimeline = DbUtils.getString(cursor, TimelineTable.ACTOR_IN_TIMELINE);
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

        timeline.lastChangedDate = DbUtils.getLong(cursor, TimelineTable.LAST_CHANGED_DATE);

        return timeline;
    }

    public static Timeline fromCommandCursor(MyContext myContext, Cursor cursor) {
        return myContext.timelines().get(
                DbUtils.getLong(cursor, CommandTable.TIMELINE_ID),
                TimelineType.load(DbUtils.getString(cursor, CommandTable.TIMELINE_TYPE)),
                DbUtils.getLong(cursor, CommandTable.ACTOR_ID),
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
                    bundle.getLong(IntentExtra.ACTOR_ID.key),
                    myContext.origins().fromId(BundleUtils.fromBundle(bundle, IntentExtra.ORIGIN_ID)),
                    BundleUtils.getString(bundle, IntentExtra.SEARCH_QUERY));
    }

    public static Timeline fromParsedUri(MyContext myContext, ParsedUri parsedUri, String searchQueryIn) {
        Timeline timeline = myContext.timelines().get(
                parsedUri.getTimelineType(),
                parsedUri.getActorId(),
                parsedUri.getOrigin(myContext),
                StringUtils.isEmpty(searchQueryIn) ? parsedUri.getSearchQuery() : searchQueryIn
        );
        if (timeline.getTimelineType() == TimelineType.UNKNOWN && parsedUri.getActorListType() == ActorListType.UNKNOWN) {
            MyLog.e(Timeline.class, "fromParsedUri; uri:" + parsedUri.getUri() + "; " + timeline);
        }
        return timeline;
    }

    private Timeline() {
        timelineType = TimelineType.UNKNOWN;
        this.myAccountToSync = MyAccount.EMPTY;
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
             long actorId, @NonNull Origin origin, String searchQuery) {
        Objects.requireNonNull(timelineType);
        Objects.requireNonNull(origin);
        this.id = id;
        this.actor = fixedActor(myContext, timelineType, actorId);
        this.origin = fixedOrigin(timelineType, origin);
        this.myAccountToSync = calcAccountToSync(myContext, timelineType, this.origin, actor);
        this.searchQuery = StringUtils.isEmpty(searchQuery) ? "" : searchQuery.trim();
        this.isCombined = calcIsCombined(timelineType, this.origin);
        this.timelineType = fixedTimelineType(timelineType);
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

    private Actor fixedActor(MyContext myContext, TimelineType timelineType, long actorId) {
        return timelineType.isForUser() ? Actor.load(myContext, actorId) : Actor.EMPTY;
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
        Timeline timeline = this;
        if (globalSearch) {
            timeline = myContext.timelines().get(TimelineType.SEARCH, this.getActorId(), this.getOrigin(),
                    this.getSearchQuery());
        }
        return timeline;
    }

    public Timeline fromIsCombined(MyContext myContext, boolean isCombinedNew) {
        if (isCombined == isCombinedNew
                || (!isCombined && timelineType.isForUser() && actor.user.isMyUser() != TriState.TRUE) ) return this;
        return myContext.timelines().get(timelineType,
                isCombinedNew ? 0 : myContext.accounts().getCurrentAccount().getActorId(),
                isCombinedNew ? Origin.EMPTY : myContext.accounts().getCurrentAccount().getOrigin(),
                searchQuery);
    }

    public Timeline fromMyAccount(MyContext myContext, MyAccount myAccountNew) {
        if (isCombined() || myAccountToSync.equals(myAccountNew)
                || (timelineType.isForUser() && actor.user.isMyUser() != TriState.TRUE)) return this;
        return myContext.timelines().get(
                timelineType,
                myAccountNew.getActorId(),
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
                    ? actor.origin
                    : MyContextHolder.get().accounts().getCurrentAccount().getOrigin();
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
            id = DbUtils.addRowWithRetry(myContext, TimelineTable.TABLE_NAME, contentValues, 3);
        } else {
            DbUtils.updateRowWithRetry(myContext, TimelineTable.TABLE_NAME, getId(), contentValues, 3);
        }
        changed = false;
        return getId();
    }

    private boolean needToLoadActorInTimeline() {
        return actor.nonEmpty()
                && (StringUtils.isEmpty(actorInTimeline) || actorInTimeline.startsWith(UriUtils.TEMP_OID_PREFIX))
                && actor.user.isMyUser().untrue;
    }

    public void delete(MyContext myContext) {
        if (isRequired() && myContext.timelines().values().stream().noneMatch(this::duplicates)) {
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
            return actor.user.isMyUser().isTrue && TimelineType.getDefaultMyAccountTimelineTypes().contains(timelineType);
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
        } else {
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
        if (StringUtils.nonEmpty(actorInTimeline)) {
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

        Timeline that = (Timeline) o;

        if (timelineType != that.timelineType) return false;
        if (id != 0 || that.id != 0) {
            return id == that.id;
        }
        if (!origin.equals(that.origin)) return false;
        if (!actor.equals(that.actor)) return false;
        return StringUtils.equalsNotEmpty(searchQuery, that.searchQuery);
    }

    public boolean duplicates(Timeline that) {
        if (equals(that)) return false;
        if (id > 0 && id < that.id) return false;
        if (timelineType != that.timelineType) return false;
        if (!origin.equals(that.origin)) return false;
        if (!actor.equals(that.actor)) return false;
        return StringUtils.equalsNotEmpty(searchQuery, that.searchQuery);
    }

    @Override
    public int hashCode() {
        int result = timelineType.hashCode();
        if (id != 0) result = 31 * result + Long.hashCode(id);
        result = 31 * result + origin.hashCode();
        result = 31 * result + actor.hashCode();
        if (!StringUtils.isEmpty(searchQuery)) result = 31 * result + searchQuery.hashCode();
        return result;
    }

    public long getActorId() {
        return actor.actorId;
    }

    public boolean hasSearchQuery() {
        return !StringUtils.isEmpty(getSearchQuery());
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
        if (!StringUtils.isEmpty(youngestPosition)) {
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

        if (!StringUtils.isEmpty(oldestPosition)) {
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
        if (syncFailedDate > 0) {
            syncFailedDate = 0;
            setChanged();
        }
    }

    public void onNewMsg(long newDate, String newPosition) {
        if (newDate <= 0 || StringUtils.isEmpty(newPosition)) {
            return;
        }
        if (youngestItemDate < newDate ||
                ( youngestItemDate == newDate && StringUtils.isNewFilledValue(youngestPosition, newPosition))) {
            youngestItemDate = newDate;
            youngestPosition = newPosition;
            setChanged();
        }
        if (oldestItemDate == 0 || oldestItemDate > newDate ||
                (oldestItemDate == newDate && StringUtils.isNewFilledValue(oldestPosition, newPosition))) {
            oldestItemDate = newDate;
            oldestPosition = newPosition;
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
        if (youngestSyncedDate < newDate) {
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
        if (oldestSyncedDate < newDate) {
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
        return myContext.timelines().get(0, getTimelineType(), ma.getActorId(), Origin.EMPTY, getSearchQuery());
    }

    public Timeline cloneForOrigin(MyContext myContext, Origin origin) {
        return myContext.timelines().get(0, getTimelineType(), 0, origin, getSearchQuery());
    }

    @NonNull
    static Timeline fromId(MyContext myContext, long id) {
        return id == 0
                ? Timeline.EMPTY
                : MyQuery.get(myContext,
                "SELECT * FROM " + TimelineTable.TABLE_NAME + " WHERE " + TimelineTable._ID + "=" + id,
                cursor -> Timeline.fromCursor(myContext, cursor)).stream().findFirst().orElse(EMPTY);
    }

    public void onSyncEnded(CommandResult result) {
        if (result.hasError()) {
            syncFailedDate = System.currentTimeMillis();
            if (!StringUtils.isEmpty(result.getMessage())) {
                errorMessage = result.getMessage();
            }
            syncFailedTimesCount++;
            syncFailedTimesCountTotal++;
        } else {
            syncSucceededDate = System.currentTimeMillis();
            syncedTimesCount++;
            syncedTimesCountTotal++;
        }
        if (result.getNewCount() > 0) {
            newItemsCount += result.getNewCount();
            newItemsCountTotal += result.getNewCount();
        }
        if (result.getDownloadedCount() > 0) {
            downloadedItemsCount += result.getDownloadedCount();
            downloadedItemsCountTotal += result.getDownloadedCount();
        }
        setChanged();
    }

    public long getSyncSucceededDate() {
        return syncSucceededDate;
    }

    public void setSyncSucceededDate(long syncSucceededDate) {
        if(this.syncSucceededDate != syncSucceededDate) {
            this.syncSucceededDate = syncSucceededDate;
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
        return Long.max(getSyncSucceededDate(), getSyncFailedDate());
    }

    @NonNull
    public String getActorInTimeline() {
        if (StringUtils.isEmpty(actorInTimeline)) {
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

    public Uri getUri() {
        return MatchedUri.getTimelineUri(this);
    }

    public Uri getClickUri() {
        return Uri.parse("content://" + TIMELINE_CLICK_HOST + getUri().getPath());
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
        } else if (timelineType == TimelineType.UNKNOWN) {
            return (actor.actorId == 0 || actor.actorId == getActorId())
                    && (origin.isEmpty() || origin.equals(getOrigin())) ;
        } else if (timelineType.isAtOrigin()) {
            return origin.isEmpty() || origin.equals(getOrigin());
        }
        return actor.actorId == 0 || actor.actorId == getActorId();
    }

    public boolean withActorProfile() {
        return !isCombined && actor.nonEmpty() && timelineType.withActorProfile();
    }
}
