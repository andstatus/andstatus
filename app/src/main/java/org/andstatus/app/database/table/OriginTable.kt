/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.provider.BaseColumns
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.SslModeEnum
import org.andstatus.app.util.TriState

/**
 * @author yvolk@yurivolkov.com
 * [org.andstatus.app.origin.Origin]
 */
object OriginTable : BaseColumns {
    val TABLE_NAME: String? = "origin"

    /** Alias for [._ID]  */
    val ORIGIN_ID: String? = "origin_id"

    /** Reference to [OriginType.getId]  */
    val ORIGIN_TYPE_ID: String? = "origin_type_id"
    val ORIGIN_NAME: String? = "origin_name"
    val ORIGIN_URL: String? = "origin_url"
    val SSL: String? = "ssl"
    val SSL_MODE: String? = "ssl_mode"
    val ALLOW_HTML: String? = "allow_html"
    val TEXT_LIMIT: String? = "text_limit"
    val MENTION_AS_WEBFINGER_ID: String? = "mention_as_webfinger_id"
    val USE_LEGACY_HTTP: String? = "use_legacy_http"

    /**
     * Include this system in Global Search while in Combined Timeline
     */
    val IN_COMBINED_GLOBAL_SEARCH: String? = "in_combined_global_search"

    /**
     * Include this system in Reload while in Combined Public Timeline
     */
    val IN_COMBINED_PUBLIC_RELOAD: String? = "in_combined_public_reload"
    fun create(db: SQLiteDatabase?) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ORIGIN_TYPE_ID + " INTEGER NOT NULL,"
                + ORIGIN_NAME + " TEXT NOT NULL,"
                + ORIGIN_URL + " TEXT NOT NULL,"
                + SSL + " BOOLEAN NOT NULL DEFAULT 1,"
                + SSL_MODE + " INTEGER NOT NULL DEFAULT " + SslModeEnum.SECURE.id + ","
                + ALLOW_HTML + " BOOLEAN NOT NULL DEFAULT 1,"
                + TEXT_LIMIT + " INTEGER NOT NULL,"
                + MENTION_AS_WEBFINGER_ID + " INTEGER NOT NULL DEFAULT " + TriState.UNKNOWN.id + ","
                + USE_LEGACY_HTTP + " INTEGER NOT NULL DEFAULT " + TriState.UNKNOWN.id + ","
                + IN_COMBINED_GLOBAL_SEARCH + " BOOLEAN NOT NULL DEFAULT 1,"
                + IN_COMBINED_PUBLIC_RELOAD + " BOOLEAN NOT NULL DEFAULT 1"
                + ")")
    }
}