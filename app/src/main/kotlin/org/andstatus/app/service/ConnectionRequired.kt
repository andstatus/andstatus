/*
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import org.andstatus.app.context.MyPreferences

enum class ConnectionRequired {
    ANY,

    /** This may be used for testing, no real command required this yet...  */
    OFFLINE, SYNC, DOWNLOAD_ATTACHMENT;

    fun isConnectionStateOk(connectionState: ConnectionState?): Boolean {
        return when (this) {
            ANY -> true
            else -> when (connectionState) {
                ConnectionState.ONLINE -> when (this) {
                    SYNC -> !MyPreferences.isSyncOverWiFiOnly()
                    DOWNLOAD_ATTACHMENT -> !MyPreferences.isSyncOverWiFiOnly()
                            && !MyPreferences.isDownloadAttachmentsOverWiFiOnly()
                    OFFLINE -> false
                    else -> true
                }
                ConnectionState.WIFI -> this != OFFLINE
                ConnectionState.OFFLINE -> this == OFFLINE
                else -> true
            }
        }
    }
}
