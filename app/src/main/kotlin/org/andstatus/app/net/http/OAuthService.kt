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

import com.github.scribejava.core.oauth.OAuth20Service
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider

/**
 * @author yvolk@yurivolkov.com
 */
interface OAuthService {
    /**
     * @return OAuth Consumer for this connection
     */
    fun getConsumer(): OAuthConsumer?

    /**
     * @return OAuth Provider for this connection
     */
    fun getProvider(): OAuthProvider?
    fun getService(redirect: Boolean): OAuth20Service?
    fun isOAuth2(): Boolean
    fun getAdditionalAuthorizationParams(): MutableMap<String, String>? = mutableMapOf()
}
