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

package org.andstatus.app.database;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
public final class OriginTable implements BaseColumns {
    public static final String TABLE_NAME = "origin";

    private OriginTable() {
    }

    /** Alias for {@link #_ID} */
    public static final String ORIGIN_ID =  "origin_id";

    /** Reference to {@link OriginType#getId()} */
    public static final String ORIGIN_TYPE_ID = "origin_type_id";
    public static final String ORIGIN_NAME = "origin_name";
    public static final String ORIGIN_URL = "origin_url";
    public static final String SSL = "ssl";
    public static final String SSL_MODE = "ssl_mode";
    public static final String ALLOW_HTML = "allow_html";
    public static final String TEXT_LIMIT = "text_limit";
    public static final String SHORT_URL_LENGTH = "short_url_length";
    public static final String MENTION_AS_WEBFINGER_ID = "mention_as_webfinger_id";
    public static final String USE_LEGACY_HTTP = "use_legacy_http";
    /**
     * Include this system in Global Search while in Combined Timeline
     */
    public static final String IN_COMBINED_GLOBAL_SEARCH = "in_combined_global_search";
    /**
     * Include this system in Reload while in Combined Public Timeline
     */
    public static final String IN_COMBINED_PUBLIC_RELOAD = "in_combined_public_reload";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + OriginTable.TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + OriginTable.ORIGIN_TYPE_ID + " INTEGER NOT NULL,"
                + OriginTable.ORIGIN_NAME + " TEXT NOT NULL,"
                + OriginTable.ORIGIN_URL + " TEXT NOT NULL,"
                + OriginTable.SSL + " BOOLEAN DEFAULT 1 NOT NULL,"
                + OriginTable.SSL_MODE + " INTEGER DEFAULT " + SslModeEnum.SECURE.getId() + " NOT NULL,"
                + OriginTable.ALLOW_HTML + " BOOLEAN DEFAULT 1 NOT NULL,"
                + OriginTable.TEXT_LIMIT + " INTEGER NOT NULL,"
                + OriginTable.SHORT_URL_LENGTH + " INTEGER NOT NULL DEFAULT 0,"
                + OriginTable.MENTION_AS_WEBFINGER_ID + " INTEGER DEFAULT " + TriState.UNKNOWN.getId() + " NOT NULL,"
                + OriginTable.USE_LEGACY_HTTP + " INTEGER DEFAULT " + TriState.UNKNOWN.getId() + " NOT NULL,"
                + OriginTable.IN_COMBINED_GLOBAL_SEARCH + " BOOLEAN DEFAULT 1 NOT NULL,"
                + OriginTable.IN_COMBINED_PUBLIC_RELOAD + " BOOLEAN DEFAULT 1 NOT NULL"
                + ")");

        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_origin_name ON " + OriginTable.TABLE_NAME + " ("
                + OriginTable.ORIGIN_NAME
                + ")");

    }
}
