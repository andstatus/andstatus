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
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.StringUtils;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import androidx.annotation.NonNull;

class Convert44 extends ConvertOneStep {
    Convert44() {
        versionTo = 47;
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
        progressLogger.logProgress(stepTitle + ": Transforming Pump.io actors");
        sql ="SELECT actor._id, username FROM actor INNER JOIN origin on actor.origin_id=origin._id" +
                " WHERE origin.origin_type_id=2";
        MyQuery.<Set<Data>>foldLeft(db, sql, new HashSet<>(), set -> cursor -> {
            Data.fromCursor(cursor).ifPresent(set::add);
            return set;
        }).forEach( data ->
            DbUtils.execSQL(db, "UPDATE actor SET username='" + data.username + "'" +
                    " WHERE _id=" + data.id)
        );

        progressLogger.logProgress(stepTitle + ": Adding previews to downloads");
        sql = "ALTER TABLE download ADD COLUMN preview_of_download_id INTEGER";
        DbUtils.execSQL(db, sql);
    }
}
