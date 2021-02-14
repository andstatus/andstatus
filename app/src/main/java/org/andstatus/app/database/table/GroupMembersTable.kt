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
package org.andstatus.app.database.table

import android.database.sqlite.SQLiteDatabase
import org.andstatus.app.data.DbUtils

/** Actors-members of the group
 * The Group is an Actor also, so [GroupMembersTable.GROUP_ID] refers to the same [ActorTable]  */
object GroupMembersTable {
    val TABLE_NAME: String? = "group_members"
    val GROUP_ID: String? = "group_id"
    val MEMBER_ID: String? = "member_id"
    fun create(db: SQLiteDatabase?) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + GROUP_ID + " INTEGER NOT NULL,"
                + MEMBER_ID + " INTEGER NOT NULL,"
                + " CONSTRAINT pk_group_members PRIMARY KEY ("
                + GROUP_ID + ", "
                + MEMBER_ID + ")"
                + ")")
        DbUtils.execSQL(db, "CREATE INDEX idx_membership ON " + TABLE_NAME + " ("
                + MEMBER_ID + ", "
                + GROUP_ID
                + ")"
        )
    }
}