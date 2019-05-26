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

package org.andstatus.app.data.converter;

import android.database.Cursor;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.StringUtils;

import java.util.Optional;

import androidx.annotation.NonNull;

class Convert48 extends ConvertOneStep {
    Convert48() {
        versionTo = 50;
    }

    private static class Data {
        final long id;
        @NonNull
        final String username;

        static Optional<Data> fromCursor(Cursor cursor) {
            final String username = DbUtils.getString(cursor, "username");
            int index = StringUtils.isEmpty(username)
                    ? -1
                    : username.indexOf("@");
            return  (index > 0)
                    ? Optional.of(new Data(DbUtils.getLong(cursor, "_id"), username.substring(0, index)))
                    : Optional.empty();
        }

        private Data(long id, @NonNull String username) {
            this.id = id;
            this.username = username;
        }
    }

    @Override
    protected void execute2() {
        progressLogger.logProgress(stepTitle + ": Adding summary and sensitive to Notes");
        sql = "ALTER TABLE note ADD COLUMN summary TEXT";
        DbUtils.execSQL(db, sql);
        sql = "ALTER TABLE note ADD COLUMN sensitive INTEGER DEFAULT 0";
        DbUtils.execSQL(db, sql);

        sql = "UPDATE note SET summary=note_name WHERE origin_id IN" +
                " (SELECT _id FROM origin WHERE origin_type_id=4)";  // Mastodon
        DbUtils.execSQL(db, sql);
        sql = "UPDATE note SET note_name=NULL, sensitive=1" +
                " WHERE (length(note_name) > 0) AND" +
                " origin_id IN (SELECT _id FROM origin WHERE origin_type_id=4)";  // Mastodon
        DbUtils.execSQL(db, sql);
    }
}
