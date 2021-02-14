package org.andstatus.app.actor;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.SqlIds;
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
import org.andstatus.app.util.StringUtil;

import static java.util.stream.Collectors.toList;

public class ActorsLoader extends SyncLoader<ActorViewItem> {
    final MyContext myContext;
    protected final ActorsScreenType actorsScreenType;
    private String searchQuery = "";
    protected final MyAccount ma;
    protected final Origin origin;
    protected boolean mAllowLoadingFromInternet = false;
    protected final long centralActorId;
    private volatile Actor centralActor = Actor.EMPTY;

    private LoadableListActivity.ProgressPublisher mProgress;

    public ActorsLoader(MyContext myContext, ActorsScreenType actorsScreenType, Origin origin, long centralActorId,
                        String searchQuery) {
        this.myContext = myContext;
        this.actorsScreenType = actorsScreenType;
        this.searchQuery = searchQuery;
        this.ma = myContext.accounts().getFirstPreferablySucceededForOrigin(origin);
        this.origin = origin;
        this.centralActorId = centralActorId;
    }

    @Override
    public void allowLoadingFromInternet() {
        mAllowLoadingFromInternet = ma.isValidAndSucceeded();
    }

    @Override
    public final void load(ProgressPublisher publisher) {
        final String method = "load";
        final StopWatch stopWatch = StopWatch.createStarted();
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, method + " started");
        }
        mProgress = publisher;
        centralActor = Actor.load(myContext, centralActorId);
        loadInternal();
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "Loaded " + size() + " items, " + stopWatch.getTime() + "ms");
        }
        if (items.isEmpty()) {
            items.add(ActorViewItem.newEmpty(myContext.context()
                        .getText(R.string.nothing_in_the_loadable_list).toString()));
        }
    }

    public Actor addActorIdToList(Origin origin, long actorId) {
        return actorId == 0 ? Actor.EMPTY : addActorToList(Actor.fromId(origin, actorId));
    }

    public Actor addActorToList(Actor actor) {
        if (actor.isEmpty()) return Actor.EMPTY;
        ActorViewItem item = ActorViewItem.fromActor(actor);
        int existing = items.indexOf(item);
        if (existing >= 0) return items.get(existing).actor;
        items.add(item);
        if (actor.actorId == 0 && mAllowLoadingFromInternet) actor.requestDownload(false);
        if (mProgress != null) {
            mProgress.publish(Integer.toString(size()));
        }
        return actor;
    }

    protected void loadInternal() {
        Uri mContentUri = MatchedUri.getActorsScreenUri(actorsScreenType, origin.getId(), centralActorId, searchQuery);
        try (Cursor c = myContext.context().getContentResolver()
                    .query(mContentUri, ActorSql.baseProjection(), getSelection(), null, null)) {
            while (c != null && c.moveToNext()) {
                populateItem(c);
            }
        }
    }

    @NonNull
    protected String getSelection() {
        SqlWhere where = new SqlWhere();
        String sqlActorIds = getSqlActorIds();
        if (StringUtil.nonEmpty(sqlActorIds)) {
            where.append(ActorTable.TABLE_NAME + "." + BaseColumns._ID + sqlActorIds);
        } else if (origin.isValid()) {
            where.append(ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.getId());
        }
        return where.getCondition();

    }

    private void populateItem(Cursor cursor) {
        ActorViewItem item = ActorViewItem.EMPTY.fromCursor(myContext, cursor);
        if (actorsScreenType == ActorsScreenType.FRIENDS) {
            item.hideFollowedBy(centralActor);
        }
        if (actorsScreenType == ActorsScreenType.FOLLOWERS) {
            item.hideFollowing(centralActor);
        }
        int index = items.indexOf(item);
        if (index < 0) {
            items.add(item);
        } else {
            items.set(index, item);
        }
    }

    protected String getSqlActorIds() {
        SqlIds sqlIds = SqlIds.fromIds(items.stream().map(ViewItem::getId).collect(toList()));
        return sqlIds.isEmpty() ? "" : sqlIds.getSql();
    }

    protected String getSubtitle() {
        return MyPreferences.isShowDebuggingInfoInUi() ? actorsScreenType.toString() : "";
    }

    @Override
    public String toString() {
        return actorsScreenType.toString()
                + "; central=" + centralActorId
                + "; " + super.toString();
    }

}
