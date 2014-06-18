/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.text.TextUtils;

import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HttpConnectionMock extends HttpConnection {
    private long postedCounter = 0;
    private JSONObject postedObject = null;
    private String pathString = "";
    private JSONObject responseObject = null;
    private ConnectionException exception = null;

    private String password = "password";
    private String userToken = "token";
    private String userSecret = "secret";
    
    public void setResponse(JSONObject jso) {
        responseObject = jso;
    }

    public void setException(ConnectionException exception) {
        this.exception = exception;
    }
    
    @Override
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        pathString = path;
        postedObject = jso;
        throwExceptionIfSet();
        return responseObject;
    }

    private void throwExceptionIfSet() throws ConnectionException {
        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public void setUserTokenWithSecret(String token, String secret) {
        userToken = token;
        userSecret = secret;
    }

    @Override
    String getUserToken() {
        return userToken;
    }

    @Override
    String getUserSecret() {
        return userSecret;
    }

    @Override
    protected JSONObject postRequest(String path) throws ConnectionException {
        postedCounter++;
        pathString = path;
        throwExceptionIfSet();
        return responseObject;
    }

    @Override
    protected JSONObject getRequest(String path) throws ConnectionException {
        postedCounter++;
        pathString = path;
        throwExceptionIfSet();
        return responseObject;
    }

    @Override
    protected JSONArray getRequestAsArray(String path) throws ConnectionException {
        pathString = path;
        JSONObject jso = getRequest(path);
        JSONArray jsa = null;
        if (jso == null) {
            throw new ConnectionException("Response is null");
        }
        if (jso.has("items")) {
            try {
                jsa = jso.getJSONArray("items");
            } catch (JSONException e) {
                throw new ConnectionException("'items' is not an array?!");
            }
        } else {
            try {
                MyLog.d(this, "Response from server: " + jso.toString(4));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            throw new ConnectionException("No array was returned");
        }
        return jsa;
    }

    @Override
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String getPassword() {
        return password;
    }
    
    @Override
    public void clearAuthInformation() {
        password = "";
        userToken = "";
        userSecret = "";
    }

    @Override
    public boolean getCredentialsPresent() {
        return !TextUtils.isEmpty(password) || ( !TextUtils.isDigitsOnly(userToken) && !TextUtils.isEmpty(userSecret));
    }

    public JSONObject getPostedJSONObject() {
        return postedObject;
    }

    public String getPathString() {
        return pathString;
    }

    public long getPostedCounter() {
        return postedCounter;
    }

    public void clearPostedData() {
        postedCounter = 0;
        postedObject = null;
        pathString = "";
    }
}
