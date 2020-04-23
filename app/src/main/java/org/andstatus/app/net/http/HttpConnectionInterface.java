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

    default Try<Void> registerClient() {
        // Do nothing in the default implementation
        return Try.success(null);
    }

    void setHttpConnectionData(HttpConnectionData data);

    default String pathToUrlString(String path) {
        // TODO: return Try
        return UrlUtils.pathToUrlString(getData().originUrl, path, errorOnInvalidUrls())
            .getOrElse("");
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
            JSONObject jso = JsonUtils.put(result.request.formParams.orElseGet(JSONObject::new), "URL", result.getUrlObj());
            MyLog.logNetworkLevelMessage("post", getData().getLogName(), jso, "");
        }
        return Try.success(result)
                .map(this::postRequest)
                .map(r -> r.logResponse(getData().getLogName()))
                .flatMap(HttpReadResult::tryToParse);
    }
    
    default HttpReadResult postRequest(HttpReadResult result) {
        return result;
    }

    default Try<JSONObject> getRequest(Uri uri) {
        return getRequestCommon(uri, true).flatMap(HttpReadResult::getJsonObject);
    }

    default Try<JSONObject> getUnauthenticatedRequest(Uri path) {
        return getRequestCommon(path, false).flatMap(HttpReadResult::getJsonObject);
    }

    default Try<HttpReadResult> getRequestCommon(Uri uri, boolean authenticated) {
        if (UriUtils.isEmpty(uri)) {
            return Try.failure(new IllegalArgumentException("URL is empty"));
        }
        MyLog.v(this, () -> "getRequest; URL='" + uri + "'");
        HttpReadResult result = new HttpReadResult(uri, new JSONObject());
        result.request.authenticate = authenticated;
        getRequest(result);
        result.logResponse(getData().getLogName());
        return result.tryToParse();
    }

    default Try<JSONArray> getRequestAsArray(Uri uri) {
        return getRequestAsArray(uri, "items");
    }

    default Try<JSONArray> getRequestAsArray(Uri uri, String parentKey) {
        return getRequestCommon(uri, true).flatMap(r -> r.getJsonArray(parentKey));
    }

    default Try<HttpReadResult> downloadFile(ConnectionRequired connectionRequired, Uri uri, File file) {
        HttpReadResult result = new HttpReadResult(getData().getMyContext(), connectionRequired, uri, file, new JSONObject());
        getRequest(result);
        result.logResponse(getData().getLogName());
        return result.tryToParse();
    }
    
    default void getRequest(HttpReadResult result) {
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
                .mapFailure(e -> new ConnectionException(ConnectionException.StatusCode.MOVED,
                        "No 'Location' header on MOVED response"))
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
