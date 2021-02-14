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
package org.andstatus.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.provider.BaseColumns
import org.andstatus.app.actor.Group
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.MyContext
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.GroupMembersTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState

/**
 * Helper class to update Group membership information (see [org.andstatus.app.database.table.GroupMembersTable])
 * @author yvolk@yurivolkov.com
 */
class GroupMembership private constructor(private val parentActor: Actor?, private val group: Actor?, private val memberId: Long, private val isMember: TriState?) {
    /**
     * Update information in the database
     */
    private fun save(myContext: MyContext?) {
        if (isMember.unknown || group.actorId == 0L || memberId == 0L || myContext == null || myContext.database == null) return
        for (pass in 0..4) {
            try {
                tryToUpdate(myContext, isMember.toBoolean(false))
                break
            } catch (e: SQLiteDatabaseLockedException) {
                MyLog.i(this, "update, Database is locked, pass=$pass", e)
                if (DbUtils.waitBetweenRetries("update")) {
                    break
                }
            }
        }
    }

    private fun tryToUpdate(myContext: MyContext?, isMember: Boolean) {
        val db = myContext.getDatabase() ?: return
        val isMemberOld = isGroupMember(myContext, group.actorId, memberId)
        if (isMemberOld == isMember) return
        if (isMemberOld) {
            db.delete(GroupMembersTable.TABLE_NAME,
                    GroupMembersTable.GROUP_ID + "=" + group.actorId + " AND " +
                            GroupMembersTable.MEMBER_ID + "=" + memberId, null)
        } else {
            if (!group.groupType.isGroupLike) return
            val cv = ContentValues()
            cv.put(GroupMembersTable.GROUP_ID, group.actorId)
            cv.put(GroupMembersTable.MEMBER_ID, memberId)
            try {
                db.insert(GroupMembersTable.TABLE_NAME, null, cv)
            } catch (e: SQLiteConstraintException) {
                MyLog.w(TAG, "Error adding a member to group " + group + ", parentActor:" + parentActor +
                        "; " + cv)
            }
        }
    }

    companion object {
        private val TAG: String? = GroupMembership::class.java.simpleName
        fun setAndReload(myContext: MyContext?, follower: Actor?, follows: TriState?, friend: Actor?) {
            if (!follower.isOidReal() || !friend.isOidReal() || follows.unknown || follower.isSame(friend)) return
            MyLog.v(TAG
            ) {
                ("Actor " + follower.getUniqueNameWithOrigin() + " "
                        + (if (follows.isTrue) "follows " else "stopped following ")
                        + friend.getUniqueNameWithOrigin())
            }
            setMember(myContext, follower, GroupType.FRIENDS, follows, friend)
            myContext.users().reload(follower)
            MyLog.v(TAG
            ) {
                ("Actor " + friend.getUniqueNameWithOrigin() + " "
                        + (if (follows.isTrue) "get a follower " else "lost a follower ")
                        + follower.getUniqueNameWithOrigin() + " (indirect info)")
            }
            setMember(myContext, friend, GroupType.FOLLOWERS, follows, follower)
            myContext.users().reload(friend)
        }

        fun setMember(myContext: MyContext?, parentActor: Actor?, groupType: GroupType?, isMember: TriState?, member: Actor?) {
            if (parentActor.actorId == 0L || member.actorId == 0L || isMember.unknown) return
            val isMember2 = if (isMember.isTrue && parentActor.isSameUser(member)) TriState.FALSE else isMember
            val group = Group.getActorsGroup(parentActor, groupType, "")
            val membership = GroupMembership(parentActor, group, member.actorId, isMember2)
            membership.save(myContext)
        }

        fun selectMemberIds(parentActorSqlIds: SqlIds?, groupType: GroupType?, includeParentId: Boolean): String? {
            return "SELECT members." + GroupMembersTable.MEMBER_ID +
                    (if (includeParentId) ", grp." + ActorTable.PARENT_ACTOR_ID else "") +
                    " FROM " + ActorTable.TABLE_NAME + " AS grp" +
                    " INNER JOIN " + GroupMembersTable.TABLE_NAME + " AS members" +
                    " ON grp." + BaseColumns._ID + "= members." + GroupMembersTable.GROUP_ID +
                    " AND grp." + ActorTable.GROUP_TYPE + "=" + groupType.id +
                    " AND grp." + ActorTable.PARENT_ACTOR_ID + parentActorSqlIds.getSql()
        }

        fun isGroupMember(parentActor: Actor?, groupType: GroupType?, memberId: Long): Boolean {
            val group = Group.getActorsGroup(parentActor, groupType, "")
            return group.nonEmpty() && isGroupMember(parentActor.origin.myContext, group.actorId, memberId)
        }

        private fun isGroupMember(myContext: MyContext?, groupId: Long, memberId: Long): Boolean {
            return MyQuery.dExists(myContext.getDatabase(), selectMembership(groupId, memberId))
        }

        private fun selectMembership(groupId: Long, memberId: Long): String? {
            return "SELECT * " +
                    " FROM " + GroupMembersTable.TABLE_NAME +
                    " WHERE " + GroupMembersTable.GROUP_ID + "=" + groupId +
                    " AND " + GroupMembersTable.MEMBER_ID + "=" + memberId
        }

        fun getGroupMemberIds(myContext: MyContext?, parentActorId: Long, groupType: GroupType?): MutableSet<Long?> {
            return MyQuery.getLongs(myContext, selectMemberIds(listOf<Long?>(parentActorId), groupType, false))
        }

        fun selectMemberIds(parentActorIds: MutableCollection<Long?>?, groupType: GroupType?, includeParentId: Boolean): String? {
            return selectMemberIds(SqlIds.Companion.fromIds(parentActorIds), groupType, includeParentId)
        }
    }
}