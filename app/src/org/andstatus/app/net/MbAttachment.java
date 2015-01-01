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

package org.andstatus.app.net;

import android.net.Uri;

import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;

public class MbAttachment {
    public String oid="";
    private Uri uri = null;
    public MyContentType contentType = MyContentType.UNKNOWN;

    public static MbAttachment fromUrlAndContentType(URL urlIn, MyContentType contentTypeIn) {
        MbAttachment attachment = new MbAttachment();
        attachment.uri = UriUtils.fromUrl(urlIn);
        attachment.contentType = MyContentType.fromUrl(urlIn, contentTypeIn);
        return attachment;
    }

    private MbAttachment() {
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
        result = prime * result + ((oid == null) ? 0 : oid.hashCode());
        result = prime * result + ((uri == null) ? 0 : uri.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MbAttachment other = (MbAttachment) obj;
        if (contentType != other.contentType) {
            return false;
        }
        if (oid == null) {
            if (other.oid != null) {
                return false;
            }
        } else if (!oid.equals(other.oid)) {
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
        return "MbAttachment [oid=" + oid + ", url=" + uri 
                + ", contentType=" + contentType + "]";
    }

    public void setUrl(URL url) {
        this.uri = UriUtils.fromUrl(url);
    }
    
    public URL getUrl() {
        return UrlUtils.fromUri(uri);
    }
}
