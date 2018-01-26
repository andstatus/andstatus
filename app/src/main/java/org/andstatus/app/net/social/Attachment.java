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

import android.net.Uri;

import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.UriUtils;

import java.net.URL;

public class Attachment {
    private Uri uri = null;
    public MyContentType contentType = MyContentType.UNKNOWN;

    public static Attachment fromUrlAndContentType(URL urlIn, MyContentType contentTypeIn) {
        return fromUriAndContentType(UriUtils.fromUrl(urlIn),
                MyContentType.fromUrl(urlIn, contentTypeIn));
    }

    public static Attachment fromUriAndContentType(Uri uriIn, MyContentType contentTypeIn) {
        Attachment attachment = new Attachment();
        if (uriIn == null) {
            throw new IllegalArgumentException("Uri is null");
        }
        if (contentTypeIn == null) {
            throw new IllegalArgumentException("MyContentType is null");
        }
        attachment.uri = uriIn;
        attachment.contentType = MyContentType.fromUri(uriIn, contentTypeIn);
        return attachment;
    }

    private Attachment() {
        // Empty
    }
    
    public boolean isValid() {
        return uri != null && contentType != MyContentType.UNKNOWN;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((contentType == null) ? 0 : contentType.hashCode());
        result = prime * result + ((uri == null) ? 0 : uri.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Attachment other = (Attachment) o;
        if (contentType != other.contentType) {
            return false;
        }
        if (uri == null) {
            if (other.uri != null) {
                return false;
            }
        } else if (!uri.equals(other.uri)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "MbAttachment [uri='" + uri
                + "', contentType=" + contentType + "]";
    }

    public void setUrl(URL url) {
        this.uri = UriUtils.fromUrl(url);
    }

    public Uri getUri() {
        return uri;
    }
}
