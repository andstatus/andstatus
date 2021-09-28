/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.user

import android.content.ContentValues
import android.database.Cursor
import android.provider.BaseColumns
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.ActorSql
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.UserTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.os.AsyncUtil
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors

/**
 * @author yvolk@yurivolkov.com
 */
class User(userId: Long, knownAs: String, isMyUser: TriState, actorIds: Set<Long>) : IsEmpty {
    var userId = 0L
    private var knownAs: String = ""
    var isMyUser: TriState = TriState.UNKNOWN
    val actorIds: MutableSet<Long>

    override val isEmpty: Boolean
        get() {
            return this === EMPTY || userId == 0L && knownAs.isEmpty()
        }

    override fun toString(): String {
        if (this === EMPTY) {
            return "User:EMPTY"
        }
        val str = User::class.simpleName!!
        var members = "id=$userId"
        if (!knownAs.isEmpty()) {
            members += "; knownAs=$knownAs"
        }
        if (isMyUser.known) {
            members += "; isMine=" + isMyUser.toBoolean(false)
        }
        return "$str{$members}"
    }

    fun getKnownAs(): String {
        return knownAs
    }

    fun save(myContext: MyContext) {
        val values = toContentValues()
        if (this === EMPTY || values.size() == 0) return
        if (userId == 0L) {
            DbUtils.addRowWithRetry(myContext, UserTable.TABLE_NAME, values, 3)
                    .onSuccess { idAdded: Long ->
                        userId = idAdded
                        MyLog.v(this) { "Added $this" }
                    }
                    .onFailure { e: Throwable -> MyLog.w(this, "Failed to add $this", e) }
        } else {
            DbUtils.updateRowWithRetry(myContext, UserTable.TABLE_NAME, userId, values, 3)
                    .onSuccess { MyLog.v(this) { "Updated $this" } }
                    .onFailure { e: Throwable -> MyLog.w(this, "Failed to update $this", e) }
        }
    }

    private fun toContentValues(): ContentValues {
        val values = ContentValues()
        if (!knownAs.isEmpty()) values.put(UserTable.KNOWN_AS, knownAs)
        if (isMyUser.known) values.put(UserTable.IS_MY, isMyUser.id)
        return values
    }

    fun setKnownAs(knownAs: String) {
        this.knownAs = knownAs
    }

    fun knownInOrigins(myContext: MyContext): MutableList<Origin> {
        return actorIds.stream().map { id: Long -> Actor.load(myContext, id) }
                .map { actor: Actor -> actor.origin }
                .filter { obj: Origin -> obj.isValid }
                .distinct()
                .sorted()
                .collect(Collectors.toList())
    }

    companion object {
        val EMPTY: User = User(0, "(empty)", TriState.UNKNOWN, emptySet<Long>())
        fun load(myContext: MyContext, actorId: Long): User {
            return myContext.users.userFromActorId(actorId) { loadInternal(myContext, actorId) }
        }

        private fun loadInternal(myContext: MyContext, actorId: Long): User {
            if (actorId == 0L || AsyncUtil.isUiThread) return EMPTY
            val sql = ("SELECT " + ActorSql.select(fullProjection = false, userOnly = true)
                    + " FROM " + ActorSql.tables(isFullProjection = false, userOnly = true, userIsOptional = false)
                    + " WHERE " + ActorTable.TABLE_NAME + "." + BaseColumns._ID + "=" + actorId)
            val function = Function { cursor: Cursor -> fromCursor(myContext, cursor, true) }
            return MyQuery[myContext, sql, function].stream().findFirst().orElse(EMPTY)
        }

        fun fromCursor(myContext: MyContext, cursor: Cursor, useCache: Boolean): User {
            val userId = DbUtils.getLong(cursor, ActorTable.USER_ID)
            val user1 = if (useCache) myContext.users.users.getOrDefault(userId, EMPTY) else EMPTY
            return if (user1.nonEmpty) user1 else User(userId, DbUtils.getString(cursor, UserTable.KNOWN_AS),
                    DbUtils.getTriState(cursor, UserTable.IS_MY),
                    loadActors(myContext, userId))
        }

        fun loadActors(myContext: MyContext, userId: Long): Set<Long> {
            return MyQuery.getLongs(myContext, "SELECT " + BaseColumns._ID
                    + " FROM " + ActorTable.TABLE_NAME
                    + " WHERE " + ActorTable.USER_ID + "=" + userId)
        }

        fun getNew(): User {
            return User(0, "", TriState.UNKNOWN, HashSet())
        }
    }

    init {
        this.userId = userId
        this.knownAs = knownAs
        this.isMyUser = isMyUser
        this.actorIds = actorIds.toMutableSet()
    }
}
