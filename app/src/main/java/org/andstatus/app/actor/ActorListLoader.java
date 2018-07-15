package org.andstatus.app.actor;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.SqlActorIds;
import org.andstatus.app.data.SqlWhere;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StopWatch;
import org.andstatus.app.util.StringUtils;

import static java.util.stream.Collectors.toList;

public class ActorListLoader extends SyncLoader<ActorViewItem> {
    final MyContext myContext;
    protected final ActorListType mActorListType;
    private String searchQuery = "";
    protected final MyAccount ma;
    protected final Origin origin;
    protected boolean mAllowLoadingFromInternet = false;
    protected final long mCentralItemId;

    private LoadableListActivity.ProgressPublisher mProgress;

    public ActorListLoader(MyContext myContext, ActorListType actorListType, MyAccount ma, Origin origin, long centralItemId, String searchQuery) {
        this.myContext = myContext;
        mActorListType = actorListType;
        this.searchQuery = searchQuery;
        this.ma = ma;
        this.origin = origin;
        mCentralItemId = centralItemId;
    }

    @Override
    public void allowLoadingFromInternet() {
        mAllowLoadingFromInternet = true;
    }

    @Override
    public final void load(ProgressPublisher publisher) {
        final String method = "load";
        final StopWatch stopWatch = StopWatch.createStarted();
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, method + " started");
        }
        mProgress = publisher;
        loadInternal();
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "Loaded " + size() + " items, " + stopWatch.getTime() + "ms");
        }
        if (items.isEmpty()) {
            addEmptyItem(myContext.context()
                    .getText(R.string.nothing_in_the_loadable_list).toString());
        }
    }

    protected void addEmptyItem(String description) {
        items.add(ActorViewItem.newEmpty(description));
    }

    public Actor addActorIdToList(Origin origin, long actorId) {
        return actorId == 0 ? Actor.EMPTY : addActorToList(Actor.fromOriginAndActorId(origin, actorId));
    }

    public Actor addActorToList(Actor actor) {
        if (actor.isEmpty()) return Actor.EMPTY;
        ActorViewItem item = ActorViewItem.fromActor(actor);
        int existing = items.indexOf(item);
        if (existing >= 0) return items.get(existing).actor;
        items.add(item);
        if (actor.actorId == 0 && mAllowLoadingFromInternet) actor.loadFromInternet();
        if (mProgress != null) {
            mProgress.publish(Integer.toString(size()));
        }
        return actor;
    }

    protected void loadInternal() {
        // TODO: Why only MyAccount's ID ??
        Uri mContentUri = MatchedUri.getActorListUri(ma.getActorId(), mActorListType, ma.getOriginId(), mCentralItemId,
                searchQuery);
        try (Cursor c = myContext.context().getContentResolver()
                    .query(mContentUri, ActorSql.projection(), getSelection(), null, null)) {
            while (c != null && c.moveToNext()) {
                populateItem(c);
            }
        }
    }

    @NonNull
    protected String getSelection() {
        SqlWhere where = new SqlWhere();
        String sqlActorIds = getSqlActorIds();
        if (StringUtils.nonEmpty(sqlActorIds)) {
            where.append(ActorTable.TABLE_NAME + "." + BaseColumns._ID + sqlActorIds);
        } else if (origin.isValid()) {
            where.append(ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + ma.getOriginId());
        }
        return where.getCondition();

    }

    private void populateItem(Cursor cursor) {
        ActorViewItem item = ActorViewItem.EMPTY.fromCursor(myContext, cursor);
        int index = items.indexOf(item);
        if (index < 0) {
            items.add(item);
        } else {
            items.set(index, item);
        }
    }

    protected String getSqlActorIds() {
        return SqlActorIds.fromIds(items.stream().map(ViewItem::getId).collect(toList())).getSql();
    }

    protected String getSubtitle() {
        return MyPreferences.isShowDebuggingInfoInUi() ? mActorListType.toString() : "";
    }

    @Override
    public String toString() {
        return mActorListType.toString()
                + "; central=" + mCentralItemId
                + "; " + super.toString();
    }

}
