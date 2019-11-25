/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.origin;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.IsEmpty;

import static org.andstatus.app.origin.OriginType.TEXT_LIMIT_MAXIMUM;

public class OriginConfig implements IsEmpty {
    public static final int MASTODON_TEXT_LIMIT_DEFAULT = 500;
    public static final int MAX_ATTACHMENTS_DEFAULT = 10;
    public static final int MAX_ATTACHMENTS_MASTODON = 4;
    public static final int MAX_ATTACHMENTS_TWITTER = 4;

    private final boolean isEmpty;
    
    public final int textLimit;
    public final long uploadLimit;
    
    public static OriginConfig getEmpty() {
        return new OriginConfig(-1, 0);
    }

    public static OriginConfig fromTextLimit(int textLimit, long uploadLimit) {
        OriginConfig config = new OriginConfig(textLimit, uploadLimit);
        return config;
    }
    
    public OriginConfig(int textLimit, long uploadLimit) {
        isEmpty = textLimit < 0;
        this.textLimit = textLimit > 0 ? textLimit : TEXT_LIMIT_MAXIMUM;
        this.uploadLimit = uploadLimit > 0 ? uploadLimit : MyPreferences.getMaximumSizeOfAttachmentBytes();
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public String toString() {
        return "OriginConfig{" +
                "textLimit=" + textLimit +
                ", uploadLimit=" + uploadLimit +
                '}';
    }

    static int getMaxAttachmentsToSend(OriginType originType) {
        switch (originType) {
            case ACTIVITYPUB:
                return MAX_ATTACHMENTS_DEFAULT;
            case MASTODON:
                return MAX_ATTACHMENTS_MASTODON;
            case TWITTER:
                return MAX_ATTACHMENTS_TWITTER;
            default:
                return 1;
        }
    }
}
