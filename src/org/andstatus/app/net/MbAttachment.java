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

import android.text.TextUtils;

import org.andstatus.app.data.ContentTypeEnum;

import java.net.URL;

public class MbAttachment {
    public String oid="";
    public URL url = null;
    public URL thumbUrl = null;
    public ContentTypeEnum contentType = ContentTypeEnum.UNKNOWN;

    // In our system
    public long originId = 0L;

    public static MbAttachment fromOriginAndOid(long originId, String oid) {
        MbAttachment attachment = new MbAttachment();
        attachment.originId = originId;
        attachment.oid = oid;
        return attachment;
    }

    private MbAttachment() {
        // Empty
    }
    
    public boolean isValid() {
        return url != null && !TextUtils.isEmpty(oid) && contentType != ContentTypeEnum.UNKNOWN && originId != 0L;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((contentType == null) ? 0 : contentType.hashCode());
        result = prime * result + ((oid == null) ? 0 : oid.hashCode());
        result = prime * result + (int) (originId ^ (originId >>> 32));
        result = prime * result + ((thumbUrl == null) ? 0 : thumbUrl.hashCode());
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MbAttachment other = (MbAttachment) obj;
        if (contentType != other.contentType)
            return false;
        if (oid == null) {
            if (other.oid != null)
                return false;
        } else if (!oid.equals(other.oid))
            return false;
        if (originId != other.originId)
            return false;
        if (thumbUrl == null) {
            if (other.thumbUrl != null)
                return false;
        } else if (!thumbUrl.equals(other.thumbUrl))
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "MbAttachment [oid=" + oid + ", url=" + url + ", thumbUrl=" + thumbUrl
                + ", contentType=" + contentType + ", originId=" + originId + "]";
    }
}
