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

package org.andstatus.app.net.http;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

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
        // Empty
    }
    
    public void setConnectionData(HttpConnectionData data) {
        this.data = data;
    }  
    
    public String pathToUrlString(String path) {
        return UrlUtils.pathToUrlString(data.originUrl, path);
    }
    
    public final JSONObject postRequest(String path) throws ConnectionException {
        return postRequest(path, null);
    }

    public final JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException {
        if (TextUtils.isEmpty(path)) {
            throw new IllegalArgumentException("path is empty");
        }
        HttpReadResult result = new HttpReadResult(pathToUrlString(path));
        result.setFormParams(formParams);
        if( result.hasFormParams()) {
            MyLog.logNetworkLevelMessage(this, "postRequest_formParams", result.getFormParams());
        }
        postRequest(result);
        MyLog.logNetworkLevelMessage(this, "postRequest_response", result.strResponse);
        result.parseAndThrow();
        return result.getJsonObject();
    }
    
    protected abstract void postRequest(HttpReadResult result)  throws ConnectionException;
    
    public final JSONObject getRequest(String path) throws ConnectionException {
        HttpReadResult result = getRequestCommon(path);
        return result.getJsonObject();
    }

    private HttpReadResult getRequestCommon(String path) throws ConnectionException {
        if (TextUtils.isEmpty(path)) {
            throw new IllegalArgumentException("path is empty");
        }
        HttpReadResult result = new HttpReadResult(pathToUrlString(path));
        getRequest(result);
        MyLog.logNetworkLevelMessage(this, "getRequest_response", result.strResponse);
        result.parseAndThrow();
        return result;
    }
    
    public final JSONArray getRequestAsArray(String path) throws ConnectionException {
        HttpReadResult result = getRequestCommon(path);
        return result.getJsonArray();
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

    public URL pathToUrl(String path) throws ConnectionException {
        return stringToUrl(pathToUrlString(path));
    }

    protected URL stringToUrl(String strUrl) throws ConnectionException {
        URL url = null;
        try {
            url = new URL(strUrl);
        } catch (MalformedURLException e) {
            throw new ConnectionException("Url: " + strUrl, e);
        }
        return url;
    }
}
