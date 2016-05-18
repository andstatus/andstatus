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

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.DownloadType;
import org.andstatus.app.data.MyContentType;

/** Avatar, Message attachment...
 */
public final class DownloadTable implements BaseColumns {
    public static final String TABLE_NAME = "download";
    private DownloadTable() {
    }
    /** See {@link DownloadType} */
    public static final String DOWNLOAD_TYPE = "download_type";
    /** Avatar is connected to exactly one user */
    public static final String USER_ID = UserTable.USER_ID;
    /** Attachment is connected to a message */
    public static final String MSG_ID =  MsgTable.MSG_ID;
    /** See {@link MyContentType} */
    public static final String CONTENT_TYPE = "content_type";
    /**
     * Date and time of the information. Used to decide which
     * (version of) information
     * is newer (we may upload older information later...)
     */
    public static final String VALID_FROM = "valid_from";
    public static final String URI = "url";
    /**
     * TODO: Drop this column on next table update as it is not used
     * Date and time there was last attempt to load this. The attempt may be successful or not.
     */
    static final String LOADED_DATE = "loaded_date";
    /**
     * See {@link DownloadStatus}. Defaults to {@link DownloadStatus#UNKNOWN}
     */
    public static final String DOWNLOAD_STATUS = "download_status";
    public static final String FILE_NAME = "file_name";

    /*
     * Derived columns (they are not stored in this table but are result of joins)
     */
    /** Alias for the primary key */
    public static final String IMAGE_ID = "image_id";

    public static final String AVATAR_FILE_NAME = "avatar_file_name";
    /** Alias helping to show first attached image */
    public static final String IMAGE_FILE_NAME = "image_file_name";
    public static final String IMAGE_URL = "image_url";
}
