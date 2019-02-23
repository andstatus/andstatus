/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.database.Cursor;
import android.net.Uri;

import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.UriUtils;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class AttachedImageFile extends ImageFile {
    public static final AttachedImageFile EMPTY = new AttachedImageFile(0, "", MediaMetadata.EMPTY,
            DownloadStatus.ABSENT, DATETIME_MILLIS_NEVER, Uri.EMPTY);
    public final Uri uri;

    public static AttachedImageFile fromCursor(Cursor cursor) {
        return new AttachedImageFile(
                DbUtils.getLong(cursor, DownloadTable.IMAGE_ID),
                DbUtils.getString(cursor, DownloadTable.IMAGE_FILE_NAME),
                MediaMetadata.fromCursor(cursor),
                DownloadStatus.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS)),
                DbUtils.getLong(cursor, DownloadTable.DOWNLOADED_DATE),
                UriUtils.fromString(DbUtils.getString(cursor, DownloadTable.IMAGE_URI))
        );
    }

    public AttachedImageFile(long downloadId, String filename, MediaMetadata mediaMetadata,
                             DownloadStatus downloadStatus, long downloadedDate, Uri uri) {
        super(filename, mediaMetadata, downloadId, downloadStatus, downloadedDate);
        this.uri = uri;
    }

    public CacheName getCacheName() {
        return CacheName.ATTACHED_IMAGE;
    }

    @Override
    protected CachedImage getDefaultImage() {
        return CachedImage.EMPTY;
    }

    @Override
    protected void requestDownload() {
        if (downloadId == 0) return;

        MyServiceManager.sendCommand(CommandData.newFetchAttachment(0, downloadId));
    }
}
