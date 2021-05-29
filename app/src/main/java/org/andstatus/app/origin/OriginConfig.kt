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
package org.andstatus.app.origin

import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.IsEmpty

class OriginConfig(textLimit: Int, uploadLimit: Long) : IsEmpty {
    val textLimit: Int = if (textLimit > 0) textLimit else TEXT_LIMIT_MAXIMUM
    val uploadLimit: Long = if (uploadLimit > 0) uploadLimit else MyPreferences.getMaximumSizeOfAttachmentBytes()

    override val isEmpty: Boolean = textLimit < 0

    override fun toString(): String {
        return "OriginConfig{" +
                "textLimit=" + textLimit +
                ", uploadLimit=" + uploadLimit +
                '}'
    }

    companion object {
        const val MASTODON_TEXT_LIMIT_DEFAULT = 500
        const val MAX_ATTACHMENTS_DEFAULT = 10
        const val MAX_ATTACHMENTS_MASTODON = 4
        const val MAX_ATTACHMENTS_TWITTER = 4
        fun getEmpty(): OriginConfig {
            return OriginConfig(-1, 0)
        }

        fun fromTextLimit(textLimit: Int, uploadLimit: Long): OriginConfig {
            return OriginConfig(textLimit, uploadLimit)
        }

        fun getMaxAttachmentsToSend(originType: OriginType): Int {
            return when (originType) {
                OriginType.ACTIVITYPUB -> MAX_ATTACHMENTS_DEFAULT
                OriginType.MASTODON -> MAX_ATTACHMENTS_MASTODON
                OriginType.TWITTER -> MAX_ATTACHMENTS_TWITTER
                else -> 1
            }
        }
    }

}
