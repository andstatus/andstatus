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

import android.text.TextUtils;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.UriUtils;
import org.json.JSONObject;

public class ConnectionMastodon extends ConnectionTwitter1p0 {
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

    @Override
    protected MbUser userFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return MbUser.getEmpty();
        }
        String oid = jso.optString("id");
        String userName = jso.optString("username");
        if (TextUtils.isEmpty(oid) || TextUtils.isEmpty(userName)) {
            throw ConnectionException.loggedJsonException(this, "Id or username is empty", null, jso);
        }
        MbUser user = MbUser.fromOriginAndUserOid(data.getOriginId(), oid);
        user.actor = MbUser.fromOriginAndUserOid(data.getOriginId(), data.getAccountUserOid());
        user.setUserName(userName);
        user.setRealName(jso.optString("display_name"));
        if (!SharedPreferencesUtil.isEmpty(user.getRealName())) {
            user.setProfileUrl(data.getOriginUrl());
        }
        user.avatarUrl = UriUtils.fromJson(jso, "avatar").toString();
        user.bannerUrl = UriUtils.fromJson(jso, "header").toString();
        user.setDescription(jso.optString("note"));
        user.setHomepage(jso.optString("url"));
        user.msgCount = jso.optLong("statuses_count");
        user.followingCount = jso.optLong("following_count");
        user.followersCount = jso.optLong("followers_count");
        user.setCreatedDate(dateFromJson(jso, "created_at"));
        return user;
    }
}
