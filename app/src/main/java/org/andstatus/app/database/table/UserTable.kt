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

package org.andstatus.app.database.table;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.andstatus.app.data.DbUtils;

public final class UserTable implements BaseColumns {
    public static final String TABLE_NAME = "user";

    private UserTable() {}

    public static final String KNOWN_AS = "user_known_as";
    public static final String IS_MY = "is_my_user";

    /*
     * Derived columns (they are not stored in this table but are result of joins)
     */
    /** Alias for the primary key */
    public static final String USER_ID = "user_id";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + KNOWN_AS + " TEXT NOT NULL DEFAULT '',"
                + IS_MY + " INTEGER NOT NULL DEFAULT 0"
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_my_user ON " + TABLE_NAME + " ("
                + IS_MY
                + ")");
    }
}
