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
package org.andstatus.app.net.http

import android.net.Uri
import com.github.scribejava.core.model.OAuthConstants
import com.github.scribejava.core.oauth.OAuth20Service
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.UriUtils
import java.util.*

class HttpConnectionOAuthMastodon : HttpConnectionOAuth2JavaNet() {
    override fun getApiUri(routine: ApiRoutineEnum?): Uri {
        var url: String = when (routine) {
            ApiRoutineEnum.OAUTH_ACCESS_TOKEN, ApiRoutineEnum.OAUTH_REQUEST_TOKEN -> data.oauthPath + "/token"
            ApiRoutineEnum.OAUTH_REGISTER_CLIENT -> data.basicPath + "/v1/apps"
            else -> super.getApiUri(routine).toString()
        }
        if (url.isNotEmpty()) {
            url = pathToUrlString(url)
        }
        return UriUtils.fromString(url)
    }

    /**
     * @see OAuth20Service.getAuthorizationUrl
     */
    override fun getAdditionalAuthorizationParams(): MutableMap<String, String> {
        val additionalParams: MutableMap<String, String> = HashMap()
        additionalParams[OAuthConstants.SCOPE] = OAUTH_SCOPES
        return additionalParams
    }
}