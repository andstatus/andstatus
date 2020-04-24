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

package org.andstatus.app.net.http;

import android.net.Uri;

import com.github.scribejava.core.exceptions.OAuthException;
import com.github.scribejava.core.extractors.OAuth2AccessTokenJsonExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.Response;

import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyOAuth2AccessTokenJsonExtractor extends OAuth2AccessTokenJsonExtractor {
    private static final Pattern ME_TOKEN_REGEX_PATTERN = Pattern.compile("\"me\"\\s*:\\s*\"(\\S*?)\"");
    private final HttpConnectionData data;

    public MyOAuth2AccessTokenJsonExtractor(HttpConnectionData data) {
        this.data = data;
    }

    @Override
    public OAuth2AccessToken extract(Response response) throws IOException {
        final String body = response.getBody();
        MyLog.logNetworkLevelMessage("oauthAccessToken_response",
                data.getLogName(ApiRoutineEnum.OAUTH_ACCESS_TOKEN), body, "");
        return super.extract(response);
    }

    public static Optional<Uri> extractWhoAmI(String response) {
        return extractParameter(response, ME_TOKEN_REGEX_PATTERN)
                .filter(StringUtil::nonEmptyNonTemp)
                .flatMap(UriUtils::toDownloadableOptional);
    }

    public static Optional<String> extractParameter(String response, Pattern regexPattern)
            throws OAuthException {
        final Matcher matcher = regexPattern.matcher(response);
        return matcher.find() ? Optional.ofNullable(matcher.group(1)) : Optional.empty();
    }
}
