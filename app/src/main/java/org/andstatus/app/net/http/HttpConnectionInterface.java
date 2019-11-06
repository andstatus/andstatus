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

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.service.ConnectionRequired;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import io.vavr.control.Try;

public interface HttpConnectionInterface {
    String USER_AGENT = "AndStatus";
    String KEY_MEDIA_PART_NAME = "media_part_name";
    String KEY_MEDIA_PART_URI = "media_part_uri";

    /**
     * The URI is consistent with "scheme" and "host" in AndroidManifest
     * Pump.io doesn't work with this scheme: "andstatus-oauth://andstatus.org"
     */
    Uri CALLBACK_URI = Uri.parse("http://oauth-redirect.andstatus.org");

    HttpConnectionData getData();

    default void registerClient() throws ConnectionException {
        // Do nothing in the default implementation
    }

    void setHttpConnectionData(HttpConnectionData data);

    default String pathToUrlString(String path) throws ConnectionException {
        return UrlUtils.pathToUrlString(getData().originUrl, path, errorOnInvalidUrls());
    }

    default boolean errorOnInvalidUrls() {
        return true;
    }

    default Try<HttpReadResult> postRequest(Uri uri) {
        return postRequest(uri, new JSONObject());
    }

    default Try<HttpReadResult> postRequest(Uri uri, JSONObject formParams) {
        /* See https://github.com/andstatus/andstatus/issues/249 */
        return (getData().getUseLegacyHttpProtocol() == TriState.UNKNOWN)
        ? postRequestOneProtocol(uri, formParams, false)
            .orElse(() -> postRequestOneProtocol(uri, formParams, true))
        : postRequestOneProtocol(uri, formParams, getData().getUseLegacyHttpProtocol().toBoolean(true));
    }

    default Try<HttpReadResult> postRequestOneProtocol(Uri path, JSONObject formParams, boolean isLegacyHttpProtocol) {
        if (UriUtils.isEmpty(path)) {
            return Try.failure(new IllegalArgumentException("URL is empty"));
        }
        HttpReadResult result = new HttpReadResult(path, formParams).setLegacyHttpProtocol(isLegacyHttpProtocol);
        if (MyPreferences.isLogNetworkLevelMessages()) {
            JSONObject jso = JsonUtils.put(result.formParams.orElseGet(JSONObject::new), "URL", result.getUrlObj());
            MyLog.logNetworkLevelMessage("post", getData().getLogName(), jso, "");
        }
        return Try.success(result)
                .map(this::postRequest)
                .map(r -> r.logResponse(getData().getLogName()))
                .map(HttpReadResult::parseAndThrow);
    }
    
    default HttpReadResult postRequest(HttpReadResult result) {
        return result;
    }

    default JSONObject getRequest(Uri uri) throws ConnectionException {
        return getRequestCommon(uri, true).getJsonObject();
    }

    default JSONObject getUnauthenticatedRequest(Uri path) throws ConnectionException {
        return getRequestCommon(path, false).getJsonObject();
    }

    default HttpReadResult getRequestCommon(Uri uri, boolean authenticated) throws ConnectionException {
        if (UriUtils.isEmpty(uri)) {
            throw new IllegalArgumentException("URL is empty");
        }
        HttpReadResult result = new HttpReadResult(uri, new JSONObject());
        result.authenticate = authenticated;
        getRequest(result);
        result.logResponse(getData().getLogName());
        result.parseAndThrow();
        return result;
    }

    default JSONArray getRequestAsArray(Uri uri) throws ConnectionException {
        return getRequestAsArray(uri, "items");
    }

    default JSONArray getRequestAsArray(Uri uri, String parentKey) throws ConnectionException {
        return getRequestCommon(uri, true).getJsonArray(parentKey);
    }

    default void downloadFile(ConnectionRequired connectionRequired, Uri uri, File file) throws ConnectionException {
        HttpReadResult result = new HttpReadResult(getData().getMyContext(), connectionRequired, uri, file, new JSONObject());
        getRequest(result);
        result.parseAndThrow();
    }
    
    default void getRequest(HttpReadResult result) throws ConnectionException {
        // Empty
    }
    
    default void clearAuthInformation() {
        // Empty
    }

    default void clearClientKeys() {
        if (getData().areOAuthClientKeysPresent()) {
            getData().oauthClientKeys.clear();
        }
    }

    default boolean isPasswordNeeded() {
        return false;
    }

    default void setPassword(String password) {
        // Nothing to do
    }
    
    /** return not null **/
    default String getPassword() {
        return "";
    }
    
    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    default boolean saveTo(AccountDataWriter dw) {
        return false;
    }

    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    default boolean getCredentialsPresent() {
        return false;
    }

    default SslModeEnum getSslMode() {
        return getData().getSslMode();
    }

    default void setUserTokenWithSecret(String token, String secret) {
        throw new IllegalArgumentException("setUserTokenWithSecret is for OAuth only!");
    }

    default String getUserToken() {
        return "";
    }

    default String getUserSecret() {
        return "";
    }

    HttpConnectionInterface getNewInstance();

    default boolean onMoved(HttpReadResult result) {
        boolean stop;
        result.appendToLog( "statusLine:'" + result.statusLine + "'");
        result.redirected = true;
        stop = TryUtils.fromOptional(result.getLocation())
                .mapFailure(e -> new IllegalArgumentException("No 'Location' header on MOVED response"))
                .map(result::setUrl)
                .onFailure(result::setException)
                .onSuccess(this::logFollowingRedirects)
                .isFailure();
        return stop;
    }

    default void logFollowingRedirects(HttpReadResult result) {
        if (MyLog.isVerboseEnabled()) {
            MyStringBuilder builder = MyStringBuilder.of("Following redirect to '" + result.getUrl());
            result.appendHeaders(builder);
            MyLog.v(this, builder.toString());
        }
    }
}
