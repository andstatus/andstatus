/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.GroupMembersTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.Collection;

import static org.andstatus.app.util.StringUtils.toTempOid;

/**
 * Helper class to update Group membership information (see {@link org.andstatus.app.database.table.GroupMembersTable})
 * @author yvolk@yurivolkov.com
 */
public class GroupMembership {
    private static final String TAG = GroupMembership.class.getSimpleName();
    private final long parentActorId;
    private final long groupId;
    private final GroupType groupType;
    private final long memberId;
    private final TriState isMember;

    public static void setMember(MyContext myContext, Actor parentActor, GroupType groupType, TriState isMember, Actor member) {
        if (parentActor.actorId == 0 || member.actorId == 0 || isMember.unknown) return;

        TriState isMember2 = isMember.isTrue && parentActor.isSameUser(member)
                ? TriState.FALSE
                : isMember;

        GroupMembership membership = new GroupMembership(parentActor.actorId, 0, groupType, member.actorId, isMember2);
        membership.save(myContext);
    }

    public static String getMembersSqlIds(long parentActorId, GroupType groupType) {
        return " IN (" + selectMemberIds(parentActorId, groupType, false) + ")";
    }

    static String selectMemberIds(long parentActorId, GroupType groupType, boolean includeParentId) {
        return selectMemberIds(SqlIds.fromId(parentActorId), groupType, includeParentId);
    }

    public static String selectMemberIds(Collection<Long> parentActorIds, GroupType groupType, boolean includeParentId) {
        return selectMemberIds(SqlIds.fromIds(parentActorIds), groupType, includeParentId);
    }

    static String selectMemberIds(SqlIds parentActorSqlIds, GroupType groupType, boolean parentIdColumn) {
        return "SELECT members." + GroupMembersTable.MEMBER_ID +
            (parentIdColumn ? ", grp." + ActorTable.PARENT_ACTOR_ID : "") +
            " FROM " + ActorTable.TABLE_NAME + " AS grp" +
            " INNER JOIN " + GroupMembersTable.TABLE_NAME + " AS members" +
            " ON grp." + ActorTable._ID + "= members." + GroupMembersTable.GROUP_ID +
            " AND grp." + ActorTable.GROUP_TYPE + "=" + groupType.id +
            " AND grp." + ActorTable.PARENT_ACTOR_ID + parentActorSqlIds.getSql();
    }

    private GroupMembership(long parentActorId, long groupId, GroupType groupType, long memberId, TriState isMember) {
        this.parentActorId = parentActorId;
        this.groupId = groupId;
        this.groupType = groupType;
        this.memberId = memberId;
        this.isMember =  isMember;
    }

    /**
     * Update information in the database 
     */
    private void save(MyContext myContext) {
        if (isMember.unknown || (parentActorId == 0 && groupId == 0) || memberId == 0 || myContext == null ||
                myContext.getDatabase() == null) return;

        for (int pass=0; pass<5; pass++) {
            try {
                tryToUpdate(myContext, isMember.toBoolean(false));
                break;
            } catch (SQLiteDatabaseLockedException e) {
                MyLog.i(this, "update, Database is locked, pass=" + pass, e);
                if (DbUtils.waitBetweenRetries("update")) {
                    break;
                }
            }
        }
    }

    private void tryToUpdate(MyContext myContext, boolean isMember) {
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) return;

        boolean isMemberOld = MyQuery.isGroupMember(myContext, parentActorId, groupType, memberId);
        if (isMemberOld == isMember) return;
        long groupIdOld = groupId == 0
            ? MyQuery.getLongs(myContext, "SELECT " + ActorTable._ID +
                " FROM " + ActorTable.TABLE_NAME +
                " WHERE " + ActorTable.PARENT_ACTOR_ID + "=" + parentActorId +
                " AND " + ActorTable.GROUP_TYPE + "=" + groupType.id)
                .stream().findAny().orElse(0L)
            : groupId;
        if (isMemberOld) {
            db.delete(GroupMembersTable.TABLE_NAME,
                GroupMembersTable.GROUP_ID + "=" + groupIdOld + " AND " +
                GroupMembersTable.MEMBER_ID + "=" + memberId, null);
        } else {
            long groupIdNew = groupIdOld == 0 ? addGroup(myContext, parentActorId, groupType) : groupIdOld;
            if (groupIdNew == 0) {
                return;
            }
            ContentValues cv = new ContentValues();
            cv.put(GroupMembersTable.GROUP_ID, groupIdNew);
            cv.put(GroupMembersTable.MEMBER_ID, memberId);
            try {
                db.insert(GroupMembersTable.TABLE_NAME, null, cv);
            } catch (SQLiteConstraintException e) {
                MyLog.w(TAG, "Error adding a member to group " + groupType + ", parentActor id:" + parentActorId +
                        "; " + cv);
            }
        }
    }

    private long addGroup(MyContext myContext, long parentActorId, GroupType groupType) {
        long originId = MyQuery.actorIdToLongColumnValue(ActorTable.ORIGIN_ID, parentActorId);
        String parentUsername = MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, parentActorId);
        String groupUsername = groupType.name + ".of." + parentUsername + "." + parentActorId;

        Actor group = Actor.fromTwoIds(
                myContext.origins().fromId(originId),
                groupType,
                0,
                toTempOid(groupUsername)
        );
        group.setUsername(groupUsername);
        group.setParentActorId(myContext, parentActorId);

        MyAccount myAccount = myContext.accounts().getFirstSucceededForOrigin(group.origin);
        AActivity activity = myAccount.getActor().update(group);
        new DataUpdater(myAccount).updateObjActor(activity, 0);
        if (group.actorId == 0) {
            MyLog.w(TAG, "Failed to add new group " + groupUsername + "; " + activity);
        }
        return group.actorId;
    }

}
