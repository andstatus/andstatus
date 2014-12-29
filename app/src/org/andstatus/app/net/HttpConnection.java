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

package org.andstatus.app.net;

import android.net.Uri;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class HttpConnection {
    protected HttpConnectionData data;

    public static final String USER_AGENT = "AndStatus";
    static final String KEY_MEDIA_PART_NAME = "media_part_name";
    static final String KEY_MEDIA_PART_URI = "media_part_uri";
    /** 
     * The URI is consistent with "scheme" and "host" in AndroidManifest
     * Pump.io doesn't work with this scheme: "andstatus-oauth://andstatus.org"
     */
    public static final Uri CALLBACK_URI = Uri.parse("http://oauth-redirect.andstatus.org");
 
    public void registerClient(String path) throws ConnectionException {
        // Empty
    }
    
    protected abstract JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException;

    protected void setConnectionData(HttpConnectionData data) {
        this.data = data;
    }  
    
    public String pathToUrl(String path) {
        return UrlUtils.pathToUrlString(data.originUrl, path);
    }
    
    protected abstract JSONObject postRequest(String path) throws ConnectionException;

    protected abstract JSONObject getRequest(String path) throws ConnectionException;
    
    protected abstract JSONArray getRequestAsArray(String path) throws ConnectionException;

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
        boolean changed = false;
        // Nothing to save in this implementation
        return changed;
    }
    
    public boolean save(JSONObject jso) throws JSONException {
        boolean changed = false;
        // Nothing to save in this implementation
        return changed;
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
}
