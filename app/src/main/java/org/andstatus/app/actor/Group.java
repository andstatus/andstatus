package org.andstatus.app.actor;

import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.UriUtils;

import java.util.Optional;

import static org.andstatus.app.util.StringUtil.nonEmptyNonTemp;
import static org.andstatus.app.util.StringUtil.toTempOid;

public final class Group {
    private static final String TAG = Group.class.getSimpleName();

    private Group() { /* Empty */ }

    @NonNull
    public static Actor getActorsGroup(Actor actor, GroupType groupType, String oid) {
        if (actor.actorId == 0 || actor.groupType.isGroupLike || !groupType.isGroupLike) {
            return Actor.EMPTY;
        }

        long groupId = getActorsGroupId(actor, groupType);
        if (groupId == 0) {
            groupId = addActorsGroup(actor.origin.myContext, actor, groupType, oid);
        }
        Actor group = actor.origin.myContext.users().load(groupId);
        return groupTypeNeedsCorrection(group.groupType, groupType)
            ? correctGroupType(actor, group, groupType)
            : group;
    }

    private static Actor correctGroupType(Actor actor, Actor group, GroupType newType) {
        MyStringBuilder msg = MyStringBuilder.of("Correct group type from " + group.groupType +
                " to " + newType + " for " + group);
        if (group.actorId == 0){
            MyLog.w(TAG, msg.prependWithSeparator("Failed, actorId==0", ". ").toString());
            return group;
        }
        SQLiteDatabase db = actor.origin.myContext.getDatabase();
        if (db == null){
            MyLog.databaseIsNull(() -> msg);
            return group;
        }

        long parentActorId = newType.parentActorRequired ? actor.actorId : 0;

        Optional<String> optOid = optOidFromEndpoint(actor, newType);
        optOid.map(oid -> msg.withComma("uri", oid));
        MyLog.i(TAG, msg.toString());

        db.execSQL("UPDATE " + ActorTable.TABLE_NAME +
                " SET " + ActorTable.GROUP_TYPE + "=" + newType.id +
                (newType.parentActorRequired == (parentActorId != 0)
                    ? ", " + ActorTable.PARENT_ACTOR_ID + "=" + parentActorId
                    : "") +
                optOid.map(oid -> ", " + ActorTable.ACTOR_OID + "='" + oid + "'").orElse("") +
                " WHERE " + ActorTable._ID + "=" + group.actorId
        );

        return actor.origin.myContext.users().reload(group);
    }

    private static Optional<String> optOidFromEndpoint(Actor actor, GroupType groupType) {
        return (groupType == GroupType.FOLLOWERS
                ? actor.getEndpoint(ActorEndpointType.API_FOLLOWERS)
                : groupType == GroupType.FRIENDS
                    ? actor.getEndpoint(ActorEndpointType.API_FOLLOWING)
                    : Optional.<Uri>empty())
            .filter(UriUtils::isDownloadable)
            .map(Uri::toString);
    }

    @NonNull
    public static Actor getGroupById(Origin origin, long groupId) {
        return Actor.fromId(origin, groupId);
    }

    private static long getActorsGroupId(Actor parentActor, GroupType groupType) {
        return MyQuery.getLongs(parentActor.origin.myContext, "SELECT " + ActorTable._ID +
            " FROM " + ActorTable.TABLE_NAME +
            " WHERE " + ActorTable.PARENT_ACTOR_ID + "=" + parentActor.actorId +
            " AND " + ActorTable.GROUP_TYPE + "=" + groupType.id)
            .stream().findAny()
        .orElseGet( () -> getGroupIdFromParentEndpoint(parentActor, groupType));
    }

    private static long getGroupIdFromParentEndpoint(Actor parentActor, GroupType groupType) {
        return optOidFromEndpoint(parentActor, groupType)
        .map(oid -> MyQuery.getLongs(parentActor.origin.myContext, "SELECT " + ActorTable._ID +
                " FROM " + ActorTable.TABLE_NAME +
                " WHERE " + ActorTable.ACTOR_OID + "='" + oid + "'" +
                " AND " + ActorTable.ORIGIN_ID + "=" + parentActor.origin.getId()
            )
            .stream().findAny()
            .orElse(0L))
        .orElse(0L);
    }

    public static boolean groupTypeNeedsCorrection(GroupType oldType, GroupType newType) {
        if (newType.precision < oldType.precision || oldType == newType) return false;

        return (newType.isGroupLike && !oldType.isGroupLike) ||
                newType.precision > oldType.precision;
    }

    private static long addActorsGroup(MyContext myContext, Actor parentActor, GroupType newType, String oidIn) {
        long originId = MyQuery.actorIdToLongColumnValue(ActorTable.ORIGIN_ID, parentActor.actorId);
        Origin origin = myContext.origins().fromId(originId);
        String parentUsername = MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, parentActor.actorId);
        String groupUsername = newType.name + ".of." + parentUsername + "." + parentActor.actorId;
        String oid = nonEmptyNonTemp(oidIn)
                ? oidIn
                : parentActor.getEndpoint(ActorEndpointType.from(newType))
                    .map(Uri::toString)
                    .orElse(toTempOid(groupUsername));

        Actor group = Actor.fromTwoIds(origin, newType, 0, oid);
        group.setUsername(groupUsername);
        group.setParentActorId(myContext, parentActor.actorId);

        MyAccount myAccount = myContext.accounts().getFirstPreferablySucceededForOrigin(origin);
        AActivity activity = myAccount.getActor().update(group);
        new DataUpdater(myAccount).updateObjActor(activity, 0);
        if (group.actorId == 0) {
            MyLog.w(TAG, "Failed to add new Actor's " + newType + " group " + groupUsername + "; " + activity);
        }
        return group.actorId;
    }
}