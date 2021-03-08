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

open class ActorsLoader(val myContext: MyContext, protected val actorsScreenType: ActorsScreenType, origin: Origin, centralActorId: Long,
                        searchQuery: String) : SyncLoader<ActorViewItem>() {
    private val searchQuery: String = ""
    protected val ma: MyAccount
    protected val origin: Origin
    protected var mAllowLoadingFromInternet = false
    protected val centralActorId: Long

    @Volatile
    private var centralActor: Actor = Actor.Companion.EMPTY
    private var mProgress: ProgressPublisher? = null
    override fun allowLoadingFromInternet() {
        mAllowLoadingFromInternet = ma.isValidAndSucceeded()
    }

    override fun load(publisher: ProgressPublisher?) {
        val method = "load"
        val stopWatch: StopWatch = StopWatch.Companion.createStarted()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "$method started")
        }
        mProgress = publisher
        centralActor = Actor.Companion.load(myContext, centralActorId)
        loadInternal()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "Loaded " + size() + " items, " + stopWatch.time + "ms")
        }
        if (items.isEmpty()) {
            items.add(ActorViewItem.Companion.newEmpty(myContext.context()
                    .getText(R.string.nothing_in_the_loadable_list).toString()))
        }
    }

    fun addActorIdToList(origin: Origin?, actorId: Long): Actor? {
        return if (actorId == 0L) Actor.Companion.EMPTY else addActorToList(Actor.Companion.fromId(origin, actorId))
    }

    fun addActorToList(actor: Actor?): Actor? {
        if (actor.isEmpty) return Actor.Companion.EMPTY
        val item: ActorViewItem = ActorViewItem.Companion.fromActor(actor)
        val existing = items.indexOf(item)
        if (existing >= 0) return items[existing].actor
        items.add(item)
        if (actor.actorId == 0L && mAllowLoadingFromInternet) actor.requestDownload(false)
        if (mProgress != null) {
            mProgress.publish(Integer.toString(size()))
        }
        return actor
    }

    protected open fun loadInternal() {
        val mContentUri: Uri = MatchedUri.Companion.getActorsScreenUri(actorsScreenType, origin.getId(), centralActorId, searchQuery)
        myContext.context().contentResolver
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
        } else if (origin.isValid()) {
            where.append(ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID + "=" + origin.getId())
        }
        return where.condition
    }

    private fun populateItem(cursor: Cursor?) {
        val item: ActorViewItem = ActorViewItem.Companion.EMPTY.fromCursor(myContext, cursor)
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
        val sqlIds: SqlIds = SqlIds.Companion.fromIds(items.stream().map { obj: ActorViewItem? -> obj.getId() }.collect(Collectors.toList()))
        return if (sqlIds.isEmpty) "" else sqlIds.sql
    }

    open fun getSubtitle(): String? {
        return if (MyPreferences.isShowDebuggingInfoInUi()) actorsScreenType.toString() else ""
    }

    override fun toString(): String {
        return (actorsScreenType.toString()
                + "; central=" + centralActorId
                + "; " + super.toString())
    }

    init {
        this.searchQuery = searchQuery
        ma = myContext.accounts().getFirstPreferablySucceededForOrigin(origin)
        this.origin = origin
        this.centralActorId = centralActorId
    }
}