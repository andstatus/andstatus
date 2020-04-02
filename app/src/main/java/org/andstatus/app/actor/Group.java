package org.andstatus.app.actor;

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

import static org.andstatus.app.util.StringUtil.nonEmptyNonTemp;
import static org.andstatus.app.util.StringUtil.toTempOid;

public final class Group {
    private static final String TAG = Group.class.getSimpleName();

    private Group() { /* Empty */ }

    @NonNull
    public static Actor getActorsGroup(Actor actor, GroupType groupType, String oid) {
        if (actor.actorId == 0 || groupType.isGroup.isFalse) {
            return Actor.EMPTY;
        }

        long groupId = getActorsGroupId(actor, groupType);
        if (groupId == 0) {
            groupId = addActorsGroup(actor.origin.myContext, actor, groupType, oid);
        }
        return Actor.fromId(actor.origin, groupId);
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
            .stream().findAny().orElse(0L);
    }

    private static long addActorsGroup(MyContext myContext, Actor parentActor, GroupType groupType, String oidIn) {
        long originId = MyQuery.actorIdToLongColumnValue(ActorTable.ORIGIN_ID, parentActor.actorId);
        Origin origin = myContext.origins().fromId(originId);
        String parentUsername = MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, parentActor.actorId);
        String groupUsername = groupType.name + ".of." + parentUsername + "." + parentActor.actorId;
        String oid = nonEmptyNonTemp(oidIn)
                ? oidIn
                : parentActor.getEndpoint(ActorEndpointType.from(groupType))
                    .map(Uri::toString)
                    .orElse(toTempOid(groupUsername));

        Actor group = Actor.fromTwoIds(origin, groupType, 0, oid);
        group.setUsername(groupUsername);
        group.setParentActorId(myContext, parentActor.actorId);

        MyAccount myAccount = myContext.accounts().getFirstSucceededForOrigin(origin);
        AActivity activity = myAccount.getActor().update(group);
        new DataUpdater(myAccount).updateObjActor(activity, 0);
        if (group.actorId == 0) {
            MyLog.w(TAG, "Failed to add new Actor's " + groupType + " group " + groupUsername + "; " + activity);
        }
        return group.actorId;
    }
}