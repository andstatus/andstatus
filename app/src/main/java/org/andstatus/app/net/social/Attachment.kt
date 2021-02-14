/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.content.ContentResolver;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.DownloadType;
import org.andstatus.app.data.FileProvider;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.UriUtils;

import java.util.Objects;
import java.util.Optional;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class Attachment implements Comparable<Attachment>, IsEmpty {
    public static final Attachment EMPTY = new Attachment(null, Uri.EMPTY, "");
    @NonNull
    public final Uri uri;
    @NonNull
    public final String mimeType;
    @NonNull
    public final MyContentType contentType;
    Attachment previewOf = Attachment.EMPTY;

    DownloadData downloadData = DownloadData.EMPTY;
    private Optional<Long> optNewDownloadNumber = Optional.empty();

    /** #previewOf cannot be set here **/
    Attachment(@NonNull DownloadData downloadData) {
        this.downloadData = downloadData;
        uri = downloadData.getUri();
        mimeType = downloadData.getMimeType();
        contentType = downloadData.getContentType();
    }

    private Attachment(ContentResolver contentResolver, @NonNull Uri uri, @NonNull String mimeType) {
        this.uri = uri;
        this.mimeType = MyContentType.uri2MimeType(contentResolver, uri, mimeType);
        contentType = MyContentType.fromUri(DownloadType.ATTACHMENT, contentResolver, uri, mimeType);
    }

    public static Attachment fromUri(String uriString) {
        return fromUriAndMimeType(uriString, "");
    }

    public static Attachment fromUri(Uri uriIn) {
        return fromUriAndMimeType(uriIn, "");
    }

    public static Attachment fromUriAndMimeType(String uriString, @NonNull String mimeTypeIn) {
        return fromUriAndMimeType(Uri.parse(uriString), mimeTypeIn);
    }

    public static Attachment fromUriAndMimeType(Uri uriIn, @NonNull String mimeTypeIn) {
        Objects.requireNonNull(uriIn);
        Objects.requireNonNull(mimeTypeIn);
        return new Attachment(myContextHolder.getNow().context().getContentResolver(), uriIn, mimeTypeIn);
    }

    Attachment setPreviewOf(@NonNull Attachment previewOf) {
        this.previewOf = previewOf;
        return this;
    }

    public boolean isValid() {
        return nonEmpty() && contentType != MyContentType.UNKNOWN;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + contentType.hashCode();
        result = prime * result + uri.hashCode();
        result = prime * result + mimeType.hashCode();
        return result;
    }

    public long getDownloadNumber() {
        return optNewDownloadNumber.orElse(downloadData.getDownloadNumber());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attachment other = (Attachment) o;
        return contentType == other.contentType && uri.equals(other.uri) && mimeType.equals(other.mimeType);
    }

    @Override
    public String toString() {
        return "Attachment [uri='" + uri + "', " + contentType + ", mime=" + mimeType + "]";
    }

    public Uri getUri() {
        return uri;
    }

    public Uri mediaUriToPost() {
        if (downloadData.isEmpty() || UriUtils.isDownloadable(getUri())) {
            return Uri.EMPTY;
        }
        return FileProvider.downloadFilenameToUri(downloadData.getFile().getFilename());
    }

    @Override
    public int compareTo(@NonNull Attachment other) {
        if (previewOf.equals(other)) return -1;
        if (other.previewOf.equals(this)) return 1;
        if (contentType != other.contentType) {
            return contentType.attachmentsSortOrder > other.contentType.attachmentsSortOrder ? 1 : -1;
        }
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return uri == Uri.EMPTY;
    }

    public long getDownloadId() {
        return downloadData.getDownloadId();
    }

    public void setDownloadNumber(long downloadNumber) {
        optNewDownloadNumber = Optional.of(downloadNumber);
    }
}
