package org.andstatus.app.actor

import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import org.andstatus.app.R
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.ActorSql
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.data.SqlIds
import org.andstatus.app.data.SqlWhere
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StopWatch
import java.util.stream.Collectors

open class ActorsLoader(val myContext: MyContext,
                        protected val actorsScreenType: ActorsScreenType,
                        protected val origin: Origin,
                        protected val centralActorId: Long,
                        private val searchQuery: String) : SyncLoader<ActorViewItem>() {
    protected val ma: MyAccount = myContext.accounts.getFirstPreferablySucceededForOrigin(origin)
    protected var mAllowLoadingFromInternet = false

    @Volatile
    private var centralActor: Actor = Actor.EMPTY
    private var mProgress: ProgressPublisher? = null
    override fun allowLoadingFromInternet() {
        mAllowLoadingFromInternet = ma.isValidAndSucceeded()
    }

    override fun load(publisher: ProgressPublisher?) : SyncLoader<ActorViewItem> {
        val method = "load"
        val stopWatch: StopWatch = StopWatch.createStarted()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "$method started")
        }
        mProgress = publisher
        centralActor = Actor.load(myContext, centralActorId)
        loadInternal()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "Loaded " + size() + " items, " + stopWatch.time + "ms")
        }
        if (items.isEmpty()) {
            items.add(ActorViewItem.newEmpty(myContext.context
                    .getText(R.string.nothing_in_the_loadable_list).toString()))
        }
        return this
    }

    fun addActorIdToList(origin: Origin, actorId: Long): Actor {
        return if (actorId == 0L) Actor.EMPTY else addActorToList(Actor.fromId(origin, actorId))
    }

    fun addActorToList(actor: Actor): Actor {
        if (actor.isEmpty) return Actor.EMPTY
        val item: ActorViewItem = ActorViewItem.fromActor(actor)
        val existing = items.indexOf(item)
        if (existing >= 0) return items[existing].actor
        items.add(item)
        if (actor.actorId == 0L && mAllowLoadingFromInternet) actor.requestDownload(false)
        mProgress?.publish(Integer.toString(size()))
        return actor
    }

    protected open fun loadInternal() {
        val mContentUri: Uri = MatchedUri.getActorsScreenUri(actorsScreenType, origin.id, centralActorId, searchQuery)
        myContext.context.contentResolver
                .query(mContentUri, ActorSql.baseProjection(), getSelection(), null, null).use { c ->
                    while (c != null && c.moveToNext()) {
                        populateItem(c)
                    }
                }
    }

    protected open fun getSelection(): String {
        val where = SqlWhere()
        val sqlActorIds = getSqlActorIds()
        if (!sqlActorIds.isNullOrEmpty()) {
            where.append(ActorTable.TABLE_NAME + "." + BaseColumns._ID + sqlActorIds)
        } else if (origin.isValid) {
            where.append(ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.id)
        }
        return where.getCondition()
    }

    private fun populateItem(cursor: Cursor) {
        val item: ActorViewItem = ActorViewItem.EMPTY.fromCursor(myContext, cursor)
        if (actorsScreenType == ActorsScreenType.FRIENDS) {
            item.hideFollowedBy(centralActor)
        }
        if (actorsScreenType == ActorsScreenType.FOLLOWERS) {
            item.hideFollowing(centralActor)
        }
        val index = items.indexOf(item)
        if (index < 0) {
            items.add(item)
        } else {
            items[index] = item
        }
    }

    protected open fun getSqlActorIds(): String? {
        val sqlIds: SqlIds = SqlIds.fromIds(items.stream().map { obj: ActorViewItem -> obj.getId() }.collect(Collectors.toList()))
        return if (sqlIds.isEmpty) "" else sqlIds.getSql()
    }

    open fun getSubtitle(): String? {
        return if (MyPreferences.isShowDebuggingInfoInUi()) actorsScreenType.toString() else ""
    }

    override fun toString(): String {
        return (actorsScreenType.toString()
                + "; central=" + centralActorId
                + "; " + super.toString())
    }

}
