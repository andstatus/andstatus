/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

/** Collections of URIs for Actors  */
object ActorEndpointTable {
    val TABLE_NAME: String = "actorendpoints"
    val ACTOR_ID: String = ActorTable.ACTOR_ID

    /** [org.andstatus.app.net.social.ActorEndpointType]  */
    val ENDPOINT_TYPE: String = "endpoint_type"

    /** Index of an endpoint of a particular [.ENDPOINT_TYPE], starting from 0  */
    val ENDPOINT_INDEX: String = "endpoint_index"
    val ENDPOINT_URI: String = "endpoint_uri"
    fun create(db: SQLiteDatabase) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + ACTOR_ID + " INTEGER NOT NULL,"
                + ENDPOINT_TYPE + " INTEGER NOT NULL,"
                + ENDPOINT_INDEX + " INTEGER NOT NULL DEFAULT 0,"
                + ENDPOINT_URI + " TEXT NOT NULL,"
                + " CONSTRAINT pk_" + TABLE_NAME + " PRIMARY KEY (" + ACTOR_ID + " ASC, " + ENDPOINT_TYPE + " ASC, " + ENDPOINT_INDEX + " ASC)"
                + ")")
    }
}