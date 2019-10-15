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
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.UriUtils;

import java.util.Collection;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class AttachedImageFile extends ImageFile {
    public static final AttachedImageFile EMPTY = new AttachedImageFile();
    public final Uri uri;
    private final long previewOfDownloadId;
    private final Uri previewOfUri;
    private final boolean previewOfIsVideo;

    private AttachedImageFile() {
        super("", MediaMetadata.EMPTY, 0, DownloadStatus.ABSENT, DATETIME_MILLIS_NEVER);
        this.uri = Uri.EMPTY;
        this.previewOfDownloadId = 0;
        this.previewOfUri = Uri.EMPTY;
        this.previewOfIsVideo = false;
    }

    public static AttachedImageFile fromCursor(Cursor cursor) {
        final long downloadId = DbUtils.getLong(cursor, DownloadTable._ID);
        return downloadId == 0
                ? EMPTY
                : new AttachedImageFile(downloadId, cursor);
    }

    private AttachedImageFile(long downloadId, Cursor cursor) {
        super(DbUtils.getString(cursor, DownloadTable.FILE_NAME),
                MediaMetadata.fromCursor(cursor), downloadId,
                DownloadStatus.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS)),
                DbUtils.getLong(cursor, DownloadTable.DOWNLOADED_DATE));
        this.uri = UriUtils.fromString(DbUtils.getString(cursor, DownloadTable.URL));
        this.previewOfDownloadId = DbUtils.getLong(cursor, DownloadTable.PREVIEW_OF_DOWNLOAD_ID);
        this.previewOfUri = Uri.EMPTY;
        this.previewOfIsVideo = false;
    }

    public AttachedImageFile(DownloadData data) {
        super(data.getFilename(), data.mediaMetadata, data.getDownloadId(), data.getStatus(), data.getDownloadedDate());
        this.uri = data.getUri();
        this.previewOfDownloadId = 0;
        this.previewOfUri = Uri.EMPTY;
        this.previewOfIsVideo = false;
    }

    @Override
    protected CachedImage getDefaultImage() {
        return CachedImage.EMPTY;
    }

    public Uri getTargetUri() {
        return previewOfUri == Uri.EMPTY ? uri : previewOfUri;
    }

    public boolean isTargetVideo() {
        return previewOfUri == Uri.EMPTY ? isVideo() : previewOfIsVideo;
    }

    @Override
    protected void requestDownload() {
        if (downloadId == 0 || uri == Uri.EMPTY || !contentType.getDownloadMediaOfThisType()) return;

        MyServiceManager.sendCommand(CommandData.newFetchAttachment(0, downloadId));
    }

    AttachedImageFile resolvePreviews(Collection<AttachedImageFile> imageFiles) {
        if (previewOfDownloadId != 0) {
            for(AttachedImageFile other: imageFiles) {
                if(previewOfDownloadId == other.downloadId) {
                    return new AttachedImageFile(this, other);
                }
            }
        }
        return this;
    }

    private AttachedImageFile(AttachedImageFile previewFile, AttachedImageFile other) {
        super(previewFile.downloadFile.getFilename(),
                previewFile.mediaMetadata,
                previewFile.downloadId,
                previewFile.downloadStatus,
                previewFile.downloadedDate);
        uri = previewFile.uri;
        this.previewOfDownloadId = previewFile.previewOfDownloadId;
        this.previewOfUri = other.uri;
        this.previewOfIsVideo = other.contentType == MyContentType.VIDEO;
    }
}
