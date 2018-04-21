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
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DownloadType;
import org.andstatus.app.data.MyContentType;

import java.util.Objects;

public class Attachment implements Comparable<Attachment> {
    @NonNull
    public final Uri uri;
    @NonNull
    public final String mimeType;
    @NonNull
    public final MyContentType contentType;
    int downloadNumber = 0;

    private Attachment(ContentResolver contentResolver, @NonNull Uri uri, @NonNull String mimeType) {
        this.uri = uri;
        this.mimeType = MyContentType.uri2MimeType(contentResolver, uri, mimeType);
        contentType = MyContentType.fromUri(DownloadType.ATTACHMENT, contentResolver, uri, mimeType);
    }

    public static Attachment fromUri(String uriString) {
        return fromUri(Uri.parse(uriString));
    }

    public static Attachment fromUri(Uri uriIn) {
        return fromUriAndContentType(uriIn, "");
    }

    public static Attachment fromUriAndContentType(Uri uriIn, @NonNull String mimeContentTypeIn) {
        Objects.requireNonNull(uriIn);
        Objects.requireNonNull(mimeContentTypeIn);
        return new Attachment(MyContextHolder.get().context().getContentResolver(), uriIn, mimeContentTypeIn);
    }

    public boolean isValid() {
        return uri != Uri.EMPTY && contentType != MyContentType.UNKNOWN;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attachment other = (Attachment) o;
        return contentType == other.contentType && uri.equals(other.uri) && mimeType.equals(other.mimeType);
    }

    @Override
    public String toString() {
        return "MbAttachment [uri='" + uri + "', contentType=" + contentType + "]";
    }

    public Uri getUri() {
        return uri;
    }

    @Override
    public int compareTo(@NonNull Attachment o) {
        if (contentType != o.contentType) {
            return contentType.attachmentsSortOrder > o.contentType.attachmentsSortOrder ? 1 : -1;
        }
        return 0;
    }

}
