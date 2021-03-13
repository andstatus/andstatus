/*
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyStorage
import org.andstatus.app.data.DbUtils
import org.andstatus.app.util.FileUtils
import org.andstatus.app.util.MyLog

internal class Convert16 : ConvertOneStep() {
    override fun execute2() {
        versionTo = 17
        val avatarsDir = MyStorage.getDataFilesDir("avatars")
        if (avatarsDir?.exists() == true) {
            FileUtils.deleteFilesRecursively(avatarsDir)
            if (!avatarsDir.delete()) {
                MyLog.e(this, "Couldn't delete " + avatarsDir.absolutePath)
            }
        }
        sql = "DROP TABLE avatar"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,user_id INTEGER,msg_id INTEGER,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_download_user ON download (user_id, download_status)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE INDEX idx_download_msg ON download (msg_id, content_type, download_status)"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE origin RENAME TO oldorigin"
        DbUtils.execSQL(db, sql)
        sql = "DROP INDEX idx_origin_name"
        DbUtils.execSQL(db, sql)
        sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN DEFAULT 0 NOT NULL,allow_html BOOLEAN DEFAULT 0 NOT NULL,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0)"
        DbUtils.execSQL(db, sql)
        sql = "CREATE UNIQUE INDEX idx_origin_name ON origin (origin_name)"
        DbUtils.execSQL(db, sql)
        sql = "INSERT INTO origin (_id, origin_type_id, origin_name, origin_url, ssl, allow_html, text_limit, short_url_length)" +
                " SELECT _id, origin_type_id, origin_name, host, ssl, allow_html, text_limit, short_url_length" +
                " FROM oldorigin"
        DbUtils.execSQL(db, sql)
        sql = "DROP TABLE oldorigin"
        DbUtils.execSQL(db, sql)
    }
}