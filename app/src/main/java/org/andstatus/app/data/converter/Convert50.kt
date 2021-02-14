/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data.converter

import android.content.ContentValues
import android.database.Cursor
import org.andstatus.app.actor.GroupType
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.util.StringUtil
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import kotlin.collections.HashSet

internal class Convert50 : ConvertOneStep() {
    private class Data {
        val friends: MutableMap<Long?, MutableSet<Long?>?>? = HashMap()
        val followers: MutableMap<Long?, MutableSet<Long?>?>? = HashMap()
        fun addFromCursor(cursor: Cursor?): Data? {
            val friendId = DbUtils.getLong(cursor, "friend_id")
            val actorId = DbUtils.getLong(cursor, "actor_id")
            if (friendId != 0L && actorId != 0L) {
                addMember(friends, actorId, friendId)
                addMember(followers, friendId, actorId)
            }
            return this
        }

        fun addMember(membership: MutableMap<Long?, MutableSet<Long?>?>?, groupId: Long, memberId: Long) {
            val members = Optional.ofNullable(membership.get(groupId)).orElseGet { HashSet() }
            members.add(memberId)
            membership[groupId] = members
        }
    }

    override fun execute2() {
        progressLogger.logProgress("$stepTitle: Extending Actor table to hold Group also")
        sql = "ALTER TABLE actor ADD COLUMN group_type INTEGER NOT NULL DEFAULT 0"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE actor ADD COLUMN parent_actor_id INTEGER NOT NULL DEFAULT 0"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE actor SET group_type=0, parent_actor_id=0"
        DbUtils.execSQL(db, sql)
        progressLogger.logProgress("$stepTitle: Converting Friendship into GroupMembers")
        sql = "CREATE TABLE group_members (group_id INTEGER NOT NULL,member_id INTEGER NOT NULL," +
                " CONSTRAINT pk_group_members PRIMARY KEY (group_id, member_id))"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_membership ON group_members (member_id, group_id)"
        DbUtils.execSQL(db, sql)
        sql = "SELECT * FROM friendship WHERE followed=1"
        val data = MyQuery.foldLeft(db, sql, Data(), { d: Data? -> Function { cursor: Cursor? -> d.addFromCursor(cursor) } })
        data.friends.entries.forEach(groupMembersCreator(GroupType.FRIENDS))
        data.followers.entries.forEach(groupMembersCreator(GroupType.FOLLOWERS))
        sql = "DROP TABLE friendship"
        DbUtils.execSQL(db, sql)
    }

    private fun groupMembersCreator(groupType: GroupType?): Consumer<MutableMap.MutableEntry<Long?, MutableSet<Long?>?>?>? {
        return label@ Consumer { entry: MutableMap.MutableEntry<Long?, MutableSet<Long?>?>? ->
            val parentActorId = entry.key
            if (entry.value.size < 2) return@label
            val originId = MyQuery.actorIdToLongColumnValue("origin_id", parentActorId)
            val parentUsername = MyQuery.actorIdToStringColumnValue("username", parentActorId)
            val groupUsername = groupType.name + ".of." + parentUsername + "." + parentActorId

            // Add group
            val values = ContentValues()
            values.put("parent_actor_id", parentActorId)
            values.put("group_type", groupType.id)
            values.put("origin_id", originId)
            values.put("user_id", 0)
            values.put("actor_oid", StringUtil.toTempOid(groupUsername))
            values.put("username", groupUsername)
            values.put("webfinger_id", "")
            values.put("actor_ins_date", System.currentTimeMillis())
            val groupId = db.insert("actor", "", values)

            // Add members
            entry.value.forEach(Consumer { memberId: Long? ->
                sql = "INSERT INTO group_members (group_id, member_id)" +
                        " VALUES (" + groupId + ", " + memberId + ")"
                DbUtils.execSQL(db, sql)
            })
        }
    }

    init {
        versionTo = 51
    }
}