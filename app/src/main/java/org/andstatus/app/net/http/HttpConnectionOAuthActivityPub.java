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

package org.andstatus.app.net.http;

import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.oauth.OAuth20Service;

import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class HttpConnectionOAuthActivityPub extends HttpConnectionOAuth2JavaNet {

    @Override
    public String getApiUrl(Connection.ApiRoutineEnum routine) throws ConnectionException {
        String url;

        switch (routine) {
            case OAUTH_ACCESS_TOKEN:
            case OAUTH_REQUEST_TOKEN:
                url = data.getOauthPath() + "/token";
                break;
            case OAUTH_REGISTER_CLIENT:
                url = data.getBasicPath() + "/v1/apps";
                break;
            default:
                url = super.getApiUrl(routine);
                break;
        }

        if (!StringUtils.isEmpty(url)) {
            url = pathToUrlString(url);
        }

        return url;
    }

    /**
     * @see OAuth20Service#getAuthorizationUrl(Map)
     */
    @Override
    public Map<String, String> getAdditionalAuthorizationParams() {
        Map<String, String> additionalParams = new HashMap<>();
        additionalParams.put(OAuthConstants.SCOPE, OAUTH_SCOPES);
        return additionalParams;
    }

}
