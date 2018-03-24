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

import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;

public class AttachedImageFile extends ImageFile {
    public static final AttachedImageFile EMPTY = new AttachedImageFile(0, "");

    private final long downloadId;

    public static AttachedImageFile fromCursor(Cursor cursor) {
        return new AttachedImageFile(
                DbUtils.getLong(cursor, DownloadTable.IMAGE_ID),
                DbUtils.getString(cursor, DownloadTable.IMAGE_FILE_NAME));
    }

    public AttachedImageFile(long downloadIdIn, String filename) {
        super(filename);
        downloadId = downloadIdIn;
    }

    public CacheName getCacheName() {
        return CacheName.ATTACHED_IMAGE;
    }

    @Override
    protected long getId() {
        return downloadId;
    }

    @Override
    protected CachedImage getDefaultImage() {
        return CachedImage.EMPTY;
    }

    @Override
    protected void requestAsyncDownload() {
        if (downloadId != 0) {
            DownloadData.asyncRequestDownload(downloadId);
        }
    }
}
