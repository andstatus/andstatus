/**
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.net.http

import org.andstatus.app.origin.OriginType

/**
 * Keys of the "AndStatus-OpenSource" application.
 * @author yvolk@yurivolkov.com
 */
class OAuthClientKeysOpenSource : OAuthClientKeysStrategy {
    private var originType: OriginType? = OriginType.UNKNOWN

    override fun initialize(connectionData: HttpConnectionData) {
        originType = connectionData.getAccountName().origin.originType
    }

    override fun getConsumerKey(): String {
        return if (originType === OriginType.TWITTER) {
            "XPHj81OgjphGlN6Jb55Kmg"
        } else {
            ""
        }
    }

    override fun getConsumerSecret(): String {
        return if (originType === OriginType.TWITTER) {
            "o2E5AYoDQhZf9qT7ctHLGihpq2ibc5bC4iFAOHURxw"
        } else {
            ""
        }
    }

    override fun toString(): String {
        return OAuthClientKeysOpenSource::class.java.simpleName
    }
}
