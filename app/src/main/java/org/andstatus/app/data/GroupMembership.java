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

import androidx.annotation.NonNull;

import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.GroupMembersTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.andstatus.app.actor.Group.getActorsGroup;

/**
 * Helper class to update Group membership information (see {@link org.andstatus.app.database.table.GroupMembersTable})
 * @author yvolk@yurivolkov.com
 */
public class GroupMembership {
    private static final String TAG = GroupMembership.class.getSimpleName();
    private final Actor parentActor;
    private final Actor group;
    private final long memberId;
    private final TriState isMember;

    public static void setMember(MyContext myContext, Actor parentActor, GroupType groupType, TriState isMember, Actor member) {
        if (parentActor.actorId == 0 || member.actorId == 0 || isMember.unknown) return;

        TriState isMember2 = isMember.isTrue && parentActor.isSameUser(member)
                ? TriState.FALSE
                : isMember;

        Actor group = getActorsGroup(parentActor, groupType, "");

        GroupMembership membership = new GroupMembership(parentActor, group, member.actorId, isMember2);
        membership.save(myContext);
    }

    static String selectMemberIds(SqlIds parentActorSqlIds, GroupType groupType, boolean includeParentId) {
        return "SELECT members." + GroupMembersTable.MEMBER_ID +
            (includeParentId ? ", grp." + ActorTable.PARENT_ACTOR_ID : "") +
            " FROM " + ActorTable.TABLE_NAME + " AS grp" +
            " INNER JOIN " + GroupMembersTable.TABLE_NAME + " AS members" +
            " ON grp." + ActorTable._ID + "= members." + GroupMembersTable.GROUP_ID +
            " AND grp." + ActorTable.GROUP_TYPE + "=" + groupType.id +
            " AND grp." + ActorTable.PARENT_ACTOR_ID + parentActorSqlIds.getSql();
    }

    private GroupMembership(Actor parentActor, Actor group, long memberId, TriState isMember) {
        this.parentActor = parentActor;
        this.group = group;
        this.memberId = memberId;
        this.isMember =  isMember;
    }

    static boolean isGroupMember(Actor parentActor, GroupType groupType, long memberId) {
        Actor group = getActorsGroup(parentActor, groupType, "");
        return group.nonEmpty() && isGroupMember(parentActor.origin.myContext, group.actorId, memberId);
    }

    private static boolean isGroupMember(MyContext myContext, long groupId, long memberId) {
        return MyQuery.dExists(myContext.getDatabase(), selectMembership(groupId, memberId));
    }

    private static String selectMembership(long groupId, long memberId) {
        return "SELECT * " +
                " FROM " + GroupMembersTable.TABLE_NAME +
                " WHERE " + GroupMembersTable.GROUP_ID + "=" + groupId +
                " AND " +  GroupMembersTable.MEMBER_ID + "=" + memberId;
    }

    @NonNull
    public static Set<Long> getGroupMemberIds(MyContext myContext, long parentActorId, GroupType groupType) {
        return MyQuery.getLongs(myContext, selectMemberIds(Collections.singletonList(parentActorId), groupType, false));
    }

    public static String selectMemberIds(Collection<Long> parentActorIds, GroupType groupType, boolean includeParentId) {
        return selectMemberIds(SqlIds.fromIds(parentActorIds), groupType, includeParentId);
    }

    /**
     * Update information in the database 
     */
    private void save(MyContext myContext) {
        if (isMember.unknown || group.actorId == 0 || memberId == 0 || myContext == null ||
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

        boolean isMemberOld = isGroupMember(myContext, group.actorId, memberId);
        if (isMemberOld == isMember) return;

        if (isMemberOld) {
            db.delete(GroupMembersTable.TABLE_NAME,
                GroupMembersTable.GROUP_ID + "=" + group.actorId + " AND " +
                GroupMembersTable.MEMBER_ID + "=" + memberId, null);
        } else {
            if (group.groupType.isGroup.isFalse) {
                return;
            }
            ContentValues cv = new ContentValues();
            cv.put(GroupMembersTable.GROUP_ID, group.actorId);
            cv.put(GroupMembersTable.MEMBER_ID, memberId);
            try {
                db.insert(GroupMembersTable.TABLE_NAME, null, cv);
            } catch (SQLiteConstraintException e) {
                MyLog.w(TAG, "Error adding a member to group " + group + ", parentActor:" + parentActor +
                        "; " + cv);
            }
        }
    }
}
