/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.net.social.pumpio.ConnectionPumpio;

public class ConnectionMastodon extends ConnectionPumpio {
    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch (routine) {
            case REGISTER_CLIENT:
                url = "apps";
                break;
            case STATUSES_HOME_TIMELINE:
                url = "statuses/home";
                break;
            case STATUSES_MENTIONS_TIMELINE:
                url = "statuses/mentions";
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "accounts/verify_credentials";
                break;
            default:
                url = "";
                break;
        }

        return prependWithBasicPath(url);
    }
}
