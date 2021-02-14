package org.andstatus.app.actor

import android.net.Uri
import android.provider.BaseColumns
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.UriUtils
import java.util.*
import java.util.function.Function

object Group {
    private val TAG: String? = Group::class.java.simpleName
    fun getActorsGroup(actor: Actor?, groupType: GroupType?, oid: String?): Actor {
        if (actor.actorId == 0L || actor.groupType.isGroupLike || !groupType.isGroupLike) {
            return Actor.Companion.EMPTY
        }
        var groupId = getActorsGroupId(actor, groupType)
        if (groupId == 0L) {
            groupId = addActorsGroup(actor.origin.myContext, actor, groupType, oid)
        }
        val group = actor.origin.myContext.users().load(groupId)
        return if (groupTypeNeedsCorrection(group.groupType, groupType)) correctGroupType(actor, group, groupType) else group
    }

    private fun correctGroupType(actor: Actor?, group: Actor?, newType: GroupType?): Actor? {
        val msg: MyStringBuilder = MyStringBuilder.Companion.of("Correct group type from " + group.groupType +
                " to " + newType + " for " + group)
        if (group.actorId == 0L) {
            MyLog.w(TAG, msg.prependWithSeparator("Failed, actorId==0", ". ").toString())
            return group
        }
        val db = actor.origin.myContext.database
        if (db == null) {
            MyLog.databaseIsNull { msg }
            return group
        }
        val parentActorId = if (newType.parentActorRequired) actor.actorId else 0
        val optOid = optOidFromEndpoint(actor, newType)
        optOid.map(Function { oid: String? -> msg.withComma("uri", oid) })
        MyLog.i(TAG, msg.toString())
        db.execSQL("UPDATE " + ActorTable.TABLE_NAME +
                " SET " + ActorTable.GROUP_TYPE + "=" + newType.id +
                (if (newType.parentActorRequired == (parentActorId != 0L)) ", " + ActorTable.PARENT_ACTOR_ID + "=" + parentActorId else "") +
                optOid.map(Function { oid: String? -> ", " + ActorTable.ACTOR_OID + "='" + oid + "'" }).orElse("") +
                " WHERE " + BaseColumns._ID + "=" + group.actorId
        )
        return actor.origin.myContext.users().reload(group)
    }

    private fun optOidFromEndpoint(actor: Actor?, groupType: GroupType?): Optional<String?>? {
        return (if (groupType == GroupType.FOLLOWERS) actor.getEndpoint(ActorEndpointType.API_FOLLOWERS) else if (groupType == GroupType.FRIENDS) actor.getEndpoint(ActorEndpointType.API_FOLLOWING) else Optional.empty())
                .filter { obj: Uri? -> UriUtils.isDownloadable() }
                .map { obj: Uri? -> obj.toString() }
    }

    fun getGroupById(origin: Origin?, groupId: Long): Actor {
        return Actor.Companion.fromId(origin, groupId)
    }

    private fun getActorsGroupId(parentActor: Actor?, groupType: GroupType?): Long {
        return MyQuery.getLongs(parentActor.origin.myContext, "SELECT " + BaseColumns._ID +
                " FROM " + ActorTable.TABLE_NAME +
                " WHERE " + ActorTable.PARENT_ACTOR_ID + "=" + parentActor.actorId +
                " AND " + ActorTable.GROUP_TYPE + "=" + groupType.id)
                .stream().findAny()
                .orElseGet { getGroupIdFromParentEndpoint(parentActor, groupType) }
    }

    private fun getGroupIdFromParentEndpoint(parentActor: Actor?, groupType: GroupType?): Long {
        return optOidFromEndpoint(parentActor, groupType)
                .map(Function { oid: String? ->
                    MyQuery.getLongs(parentActor.origin.myContext, "SELECT " + BaseColumns._ID +
                            " FROM " + ActorTable.TABLE_NAME +
                            " WHERE " + ActorTable.ACTOR_OID + "='" + oid + "'" +
                            " AND " + ActorTable.ORIGIN_ID + "=" + parentActor.origin.id
                    )
                            .stream().findAny()
                            .orElse(0L)
                })
                .orElse(0L)
    }

    fun groupTypeNeedsCorrection(oldType: GroupType?, newType: GroupType?): Boolean {
        return if (newType.precision < oldType.precision || oldType == newType) false else newType.isGroupLike && !oldType.isGroupLike ||
                newType.precision > oldType.precision
    }

    private fun addActorsGroup(myContext: MyContext?, parentActor: Actor?, newType: GroupType?, oidIn: String?): Long {
        val originId = MyQuery.actorIdToLongColumnValue(ActorTable.ORIGIN_ID, parentActor.actorId)
        val origin = myContext.origins().fromId(originId)
        val parentUsername = MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, parentActor.actorId)
        val groupUsername = newType.name + ".of." + parentUsername + "." + parentActor.actorId
        val oid = if (StringUtil.nonEmptyNonTemp(oidIn)) oidIn else parentActor.getEndpoint(ActorEndpointType.Companion.from(newType))
                .map { obj: Uri? -> obj.toString() }
                .orElse(StringUtil.toTempOid(groupUsername))
        val group: Actor = Actor.Companion.fromTwoIds(origin, newType, 0, oid)
        group.username = groupUsername
        group.setParentActorId(myContext, parentActor.actorId)
        val myAccount = myContext.accounts().getFirstPreferablySucceededForOrigin(origin)
        val activity = myAccount.actor.update(group)
        DataUpdater(myAccount).updateObjActor(activity, 0)
        if (group.actorId == 0L) {
            MyLog.w(TAG, "Failed to add new Actor's $newType group $groupUsername; $activity")
        }
        return group.actorId
    }
}