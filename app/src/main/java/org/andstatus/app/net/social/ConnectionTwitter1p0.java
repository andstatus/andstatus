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

package org.andstatus.app.net.social;

import org.andstatus.app.net.http.ConnectionException;
import org.json.JSONObject;

/**
 * Twitter API v.1 https://dev.twitter.com/docs/api/1
 *
 */
public class ConnectionTwitter1p0 extends ConnectionTwitter {

    @Override
    public MbMessage createFavorite(String statusId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithMessageId(ApiRoutineEnum.CREATE_FAVORITE, statusId));
        return messageFromJson(jso);
    }

    @Override
    public MbMessage destroyFavorite(String statusId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithMessageId(ApiRoutineEnum.DESTROY_FAVORITE, statusId));
        return messageFromJson(jso);
    }


    private String getApiPathWithMessageId(ApiRoutineEnum routineEnum, String userId) throws ConnectionException {
        return getApiPath(routineEnum).replace("%messageId%", userId);
    }

}
