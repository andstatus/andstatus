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

package org.andstatus.app.service;

import org.andstatus.app.context.MyPreferences;

public enum ConnectionRequired {
    ANY,
    /** This may be used for testing, no real command required this yet... */
    OFFLINE,
    SYNC,
    DOWNLOAD_ATTACHMENT;

    public boolean isConnectionStateOk(ConnectionState connectionState) {
        switch (this) {
            case ANY:
                return true;
            default:
                switch (connectionState) {
                    case ONLINE:
                        switch (this) {
                            case SYNC:
                                return !MyPreferences.isSyncOverWiFiOnly();
                            case DOWNLOAD_ATTACHMENT:
                                return !MyPreferences.isSyncOverWiFiOnly()
                                        && !MyPreferences.isDownloadAttachmentsOverWiFiOnly();
                            case OFFLINE:
                                return false;
                            default:
                                return true;
                        }
                    case WIFI:
                        return (this != ConnectionRequired.OFFLINE);
                    case OFFLINE:
                        return (this == ConnectionRequired.OFFLINE);
                    default:
                        return true;
                }
        }
    }
}
