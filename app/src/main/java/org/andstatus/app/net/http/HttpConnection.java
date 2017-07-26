/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.TextUtils;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public abstract class HttpConnection {
    public HttpConnectionData data;

    public static final String USER_AGENT = "AndStatus";
    public static final String KEY_MEDIA_PART_NAME = "media_part_name";
    public static final String KEY_MEDIA_PART_URI = "media_part_uri";
    /** 
     * The URI is consistent with "scheme" and "host" in AndroidManifest
     * Pump.io doesn't work with this scheme: "andstatus-oauth://andstatus.org"
     */
    public static final Uri CALLBACK_URI = Uri.parse("http://oauth-redirect.andstatus.org");
 
    public void registerClient(String path) throws ConnectionException {
        // Do nothing in default in the Base implementation
    }
    
    public void setConnectionData(HttpConnectionData data) {
        this.data = data;
    }  
    
    public String pathToUrlString(String path) throws ConnectionException {
        return UrlUtils.pathToUrlString(data.originUrl, path, errorOnInvalidUrls());
    }

    public boolean errorOnInvalidUrls() {
        return true;
    }

    public final JSONObject postRequest(String path) throws ConnectionException {
        return postRequest(path, null);
    }

    public final JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException {
        /* See https://github.com/andstatus/andstatus/issues/249 */
        if (data.getUseLegacyHttpProtocol() == TriState.UNKNOWN) {
            try {
                return postRequestOneHttpProtocol(path, formParams, false);
            } catch (ConnectionException e) {
                if (e.getStatusCode() != StatusCode.LENGTH_REQUIRED) {
                    throw e;
                }
                MyLog.v(this, "Automatic fallback to legacy HTTP", e);
            }
        }
        return postRequestOneHttpProtocol(path, formParams, data.getUseLegacyHttpProtocol().toBoolean(true));
    }

    private JSONObject postRequestOneHttpProtocol(String path, JSONObject formParams, 
            boolean isLegacyHttpProtocol ) throws ConnectionException {
        if (TextUtils.isEmpty(path)) {
            throw new IllegalArgumentException("path is empty");
        }
        HttpReadResult result = new HttpReadResult(pathToUrlString(path)).setFormParams(formParams)
                .setLegacyHttpProtocol(isLegacyHttpProtocol);
        if( result.hasFormParams()) {
            MyLog.logNetworkLevelMessage("post_form", data.getLogName(), result.getFormParams());
        }
        postRequest(result);
        MyLog.logNetworkLevelMessage("post_response", data.getLogName(), result.strResponse);
        result.parseAndThrow();
        return result.getJsonObject();
    }
    
    protected abstract void postRequest(HttpReadResult result) throws ConnectionException;
    
    public final JSONObject getRequest(String path) throws ConnectionException {
        return getRequestCommon(path, true).getJsonObject();
    }

    public final JSONObject getUnauthenticatedRequest(String path) throws ConnectionException {
        return getRequestCommon(path, false).getJsonObject();
    }
    
    private HttpReadResult getRequestCommon(String path, boolean authenticated) throws ConnectionException {
        if (TextUtils.isEmpty(path)) {
            throw new IllegalArgumentException("path is empty");
        }
        HttpReadResult result = new HttpReadResult(pathToUrlString(path));
        result.authenticate = authenticated;
        getRequest(result);
        MyLog.logNetworkLevelMessage("get_response", data.getLogName(), result.strResponse);
        result.parseAndThrow();
        return result;
    }

    public final JSONArray getRequestAsArray(String path) throws ConnectionException {
        return getRequestAsArray(path, "items");
    }

    public final JSONArray getRequestAsArray(String path, String parentKey) throws ConnectionException {
        return getRequestCommon(path, true).getJsonArray(parentKey);
    }

    public final void downloadFile(String url, File file) throws ConnectionException {
        HttpReadResult result = new HttpReadResult(url, file);
        getRequest(result);
        result.parseAndThrow();
    }
    
    protected abstract void getRequest(HttpReadResult result) throws ConnectionException;
    
    public abstract void clearAuthInformation();

    public void clearClientKeys() {
        if (data.areOAuthClientKeysPresent()) {
            data.oauthClientKeys.clear();
        }
    }
    
    public boolean isPasswordNeeded() {
        return false;
    }
    
    public void setPassword(String password) {
        // Nothing to do
    }
    
    /** return not null **/
    public String getPassword() {
        return "";
    }
    
    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    public boolean save(AccountDataWriter dw) {
        return false;
    }

    /** @return true if changed */
    public boolean save(JSONObject jso) throws JSONException {
        return false;
    }
    
    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    public abstract boolean getCredentialsPresent();

    public SslModeEnum getSslMode() {
        return data.getSslMode();
    }
    
    public void setUserTokenWithSecret(String token, String secret) {
        throw new IllegalArgumentException("setUserTokenWithSecret is for OAuth only!");
    }

    String getUserToken() {
        return "";
    }

    String getUserSecret() {
        return "";
    }

    public HttpConnection getNewInstance() {
        try {
            return getClass().newInstance();
        } catch (InstantiationException e) {
            MyLog.e(this, e);
        } catch (IllegalAccessException e) {
            MyLog.e(this, e);
        }
        return null;
    }
}
