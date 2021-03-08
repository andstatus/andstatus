/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social

import android.database.Cursor
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable

/** @author yvolk@yurivolkov.com
 */
enum class Visibility(val id: Long) {
    UNKNOWN(3), PUBLIC_AND_TO_FOLLOWERS(4), PUBLIC(5), NOT_PUBLIC_NEEDS_CLARIFICATION(6), TO_FOLLOWERS(8), PRIVATE(10);

    fun isKnown(): Boolean {
        return this != UNKNOWN
    }

    fun isPublicCheckbox(): Boolean {
        return this == UNKNOWN || isPublic()
    }

    fun isPublic(): Boolean {
        return when (this) {
            PUBLIC_AND_TO_FOLLOWERS, PUBLIC -> true
            else -> false
        }
    }

    fun isFollowers(): Boolean {
        return when (this) {
            PUBLIC_AND_TO_FOLLOWERS, TO_FOLLOWERS -> true
            else -> false
        }
    }

    fun getKnown(): Visibility {
        val v1 = if (isPublic()) PUBLIC else PRIVATE
        return if (isFollowers()) v1.add(TO_FOLLOWERS) else v1
    }

    fun add(other: Visibility): Visibility {
        if (this == other) return this
        if (this == UNKNOWN) return other
        if (other == UNKNOWN) return this
        if (this == NOT_PUBLIC_NEEDS_CLARIFICATION) return other
        return if (other == NOT_PUBLIC_NEEDS_CLARIFICATION) this else when (other) {
            PUBLIC -> if (id <= id) PUBLIC_AND_TO_FOLLOWERS else PUBLIC
            TO_FOLLOWERS -> if (id <= id) PUBLIC_AND_TO_FOLLOWERS else TO_FOLLOWERS
            UNKNOWN -> this
            else -> if (id < other.id) this else other
        }
    }

    val isPrivate: Boolean get() = this == PRIVATE

    companion object {
        fun fromId(id: Long): Visibility {
            // Special handling of values, created before v.55
            if (id == 2L) return PUBLIC
            if (id == 1L) return NOT_PUBLIC_NEEDS_CLARIFICATION
            for (value in values()) {
                if (value.id == id) {
                    return value
                }
            }
            return UNKNOWN
        }

        fun fromNoteId(noteId: Long): Visibility {
            return fromId(MyQuery.noteIdToLongColumnValue(NoteTable.VISIBILITY, noteId))
        }

        fun fromCursor(cursor: Cursor?): Visibility {
            return fromId(DbUtils.getLong(cursor, NoteTable.VISIBILITY))
        }

        fun fromCheckboxes(isPublic: Boolean, isFollowers: Boolean): Visibility {
            return if (isPublic) {
                if (isFollowers) PUBLIC_AND_TO_FOLLOWERS else PUBLIC
            } else {
                if (isFollowers) TO_FOLLOWERS else PRIVATE
            }
        }
    }
}