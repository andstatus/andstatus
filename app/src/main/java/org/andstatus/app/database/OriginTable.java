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

import android.provider.BaseColumns;

import org.andstatus.app.origin.OriginType;

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
}
