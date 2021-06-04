/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
import com.github.scribejava.core.exceptions.OAuthException
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.Response
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.UriUtils
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

class MyOAuth2AccessTokenJsonExtractor(private val data: HttpConnectionData) : OAuth2AccessTokenJsonExtractor() {
    @Throws(IOException::class)
    override fun extract(response: Response): OAuth2AccessToken? {
        val body = response.body
        MyLog.logNetworkLevelMessage("oauthAccessToken_response",
                HttpRequest.of(ApiRoutineEnum.OAUTH_ACCESS_TOKEN, Uri.EMPTY)
                        .withConnectionData(data).getLogName(), body, "")
        return super.extract(response)
    }

    companion object {
        private val ME_TOKEN_REGEX_PATTERN = Pattern.compile("\"me\"\\s*:\\s*\"(\\S*?)\"")

        fun extractWhoAmI(response: String): Optional<Uri> {
            return extractParameter(response, ME_TOKEN_REGEX_PATTERN)
                    .filter { obj: String? -> StringUtil.nonEmptyNonTemp(obj) }
                    .flatMap { obj: String? -> UriUtils.toDownloadableOptional(obj) }
        }

        @Throws(OAuthException::class)
        fun extractParameter(response: String, regexPattern: Pattern): Optional<String> {
            val matcher = regexPattern.matcher(response)
            return if (matcher.find()) Optional.ofNullable(matcher.group(1)) else Optional.empty()
        }
    }
}
