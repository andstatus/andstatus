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
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpConnectionMock extends HttpConnection {
    private volatile long postedCounter = 0;
    private volatile JSONObject postedObject = null;
    private final List<String> pathStringList = new CopyOnWriteArrayList<String>();
    private volatile JSONObject responseObject = null;
    private volatile ConnectionException exception = null;

    private volatile String password = "password";
    private volatile String userToken = "token";
    private volatile String userSecret = "secret";
    
    private volatile long networkDelayMs = 1000;
    protected final long mInstanceId = InstanceId.next(); 
    
    public HttpConnectionMock() {
        MyLog.v(this, "Created, instanceId:" + mInstanceId);
    }
    
    public void setResponse(JSONObject jso) {
        responseObject = jso;
    }

    public void setException(ConnectionException exception) {
        this.exception = exception;
    }
    
    @Override
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        onRequest("postRequestWithObject", path);
        postedObject = jso;
        throwExceptionIfSet();
        return responseObject;
    }

    private void networkDelay() {
        try {
            Thread.sleep(networkDelayMs);
        } catch (InterruptedException e) {
            MyLog.v(this, "networkDelay", e);
        }
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
        onRequest("postRequest", path);
        throwExceptionIfSet();
        return responseObject;
    }

    private void onRequest(String method, String path) {
        postedCounter++;
        MyLog.v(this, method + " num:" + postedCounter + "; path:'" + path +"', host:'" 
        + data.host + "', instanceId:" + mInstanceId );
        MyLog.v(this, Arrays.toString(Thread.currentThread().getStackTrace()));
        pathStringList.add(path);
        networkDelay();
    }

    @Override
    protected JSONObject getRequest(String path) throws ConnectionException {
        return getRequestInner("getRequest", path);
    }

    private JSONObject getRequestInner(String method, String path) throws ConnectionException {
        onRequest(method, path);
        throwExceptionIfSet();
        return responseObject;
    }
    
    @Override
    protected JSONArray getRequestAsArray(String path) throws ConnectionException {
        JSONObject jso = getRequestInner("getRequestAsArray", path);
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

    public List<String> getPathStringList() {
        return pathStringList;
    }

    public long getPostedCounter() {
        return postedCounter;
    }

    public void clearPostedData() {
        postedCounter = 0;
        postedObject = null;
        pathStringList.clear();
    }

    public long getInstanceId() {
        return mInstanceId;
    }

    @Override
    public HttpConnection getNewInstance() {
        return this;
    }
}
