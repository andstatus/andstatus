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

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.UriUtils;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class AttachedMediaFile extends MediaFile {
    public static final AttachedMediaFile EMPTY = new AttachedMediaFile();
    public final Uri uri;
    final long previewOfDownloadId;
    public final AttachedMediaFile previewOf;

    private AttachedMediaFile() {
        super("", MyContentType.UNKNOWN, MediaMetadata.EMPTY, 0, DownloadStatus.ABSENT, DATETIME_MILLIS_NEVER);
        uri = Uri.EMPTY;
        previewOfDownloadId = 0;
        previewOf = AttachedMediaFile.EMPTY;
    }

    public static AttachedMediaFile fromCursor(Cursor cursor) {
        final long downloadId = DbUtils.getLong(cursor, DownloadTable._ID);
        return downloadId == 0
                ? EMPTY
                : new AttachedMediaFile(downloadId, cursor);
    }

    private AttachedMediaFile(long downloadId, Cursor cursor) {
        super(DbUtils.getString(cursor, DownloadTable.FILE_NAME),
                MyContentType.load(DbUtils.getLong(cursor, DownloadTable.CONTENT_TYPE)),
                MediaMetadata.fromCursor(cursor), downloadId,
                DownloadStatus.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS)),
                DbUtils.getLong(cursor, DownloadTable.DOWNLOADED_DATE));
        uri = UriUtils.fromString(DbUtils.getString(cursor, DownloadTable.URL));
        previewOfDownloadId = DbUtils.getLong(cursor, DownloadTable.PREVIEW_OF_DOWNLOAD_ID);
        previewOf = AttachedMediaFile.EMPTY;
    }

    public AttachedMediaFile(DownloadData data) {
        super(data.getFilename(), data.getContentType(), data.mediaMetadata, data.getDownloadId(), data.getStatus(),
                data.getDownloadedDate());
        this.uri = data.getUri();
        this.previewOfDownloadId = 0;
        previewOf = AttachedMediaFile.EMPTY;
    }

    @Override
    protected CachedImage getDefaultImage() {
        return CachedImage.EMPTY;
    }

    public Uri getTargetUri() {
        return previewOf.isEmpty() ? uri : previewOf.uri;
    }

    public boolean isTargetVideo() {
        return previewOf.isEmpty() ? isVideo() : previewOf.isVideo();
    }

    @Override
    protected void requestDownload() {
        if (downloadId == 0 || uri == Uri.EMPTY || !contentType.getDownloadMediaOfThisType()) return;

        MyServiceManager.sendCommand(CommandData.newFetchAttachment(0, downloadId));
    }

    AttachedMediaFile(AttachedMediaFile previewFile, AttachedMediaFile previewOf) {
        super(previewFile.downloadFile.getFilename(),
                previewFile.contentType,
                previewFile.mediaMetadata,
                previewFile.downloadId,
                previewFile.downloadStatus,
                previewFile.downloadedDate);
        uri = previewFile.uri;
        this.previewOfDownloadId = previewFile.previewOfDownloadId;
        this.previewOf = previewOf;
    }

    public boolean imageOrLinkMayBeShown() {
        return contentType.isImage() &&
                (super.imageMayBeShown() || uri != Uri.EMPTY);
    }

    public Intent intentToView() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        AttachedMediaFile fileToView = previewOf.isEmpty() ? this : previewOf;
        final Uri mediaFileUri = fileToView.downloadFile.existsNow()
                ? FileProvider.downloadFilenameToUri(fileToView.downloadFile.getFilename())
                : fileToView.uri;
        if (UriUtils.isEmpty(mediaFileUri)) {
            intent.setType("text/*");
        } else {
            intent.setDataAndType(mediaFileUri, fileToView.contentType.generalMimeType);
            intent.putExtra(Intent.EXTRA_STREAM, mediaFileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return intent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AttachedMediaFile imageFile = (AttachedMediaFile) o;

        if (previewOfDownloadId != imageFile.previewOfDownloadId) return false;
        return uri.equals(imageFile.uri);
    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + (int) (previewOfDownloadId ^ (previewOfDownloadId >>> 32));
        return result;
    }
}
