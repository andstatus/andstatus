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

import org.andstatus.app.account.AccountDataWriter;
import org.json.JSONArray;
import org.json.JSONObject;

public abstract class HttpConnection {
    protected static final Integer DEFAULT_GET_REQUEST_TIMEOUT = 15000;
    protected static final Integer DEFAULT_POST_REQUEST_TIMEOUT = 20000;
    
    protected HttpConnectionData data;

    static final String USER_AGENT = "AndStatus";
 
    protected abstract JSONObject postRequest(String path, JSONObject jso) throws ConnectionException;

    protected void setConnectionData(HttpConnectionData data) {
        this.data = data;
    }  
    
    public String pathToUrl(String path) {
        if (path.contains("://")) {
            return path;
        } else {
            return "http" + (data.isHttps ? "s" : "")
                    + "://" + data.host
                    + "/" + path;
        }
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
    
    public void setPassword(String password) { }
    
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
    
    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    public abstract boolean getCredentialsPresent();

    public void setUserTokenWithSecret(String token, String secret) {
        throw(new IllegalArgumentException("setUserTokenWithSecret is for OAuth only!"));
    }

    String getUserToken() {
        return "";
    }

    String getUserSecret() {
        return "";
    }
}
