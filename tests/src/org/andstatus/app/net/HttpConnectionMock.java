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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpConnectionMock extends HttpConnection {
    private volatile long mPostedCounter = 0;
    private volatile JSONObject mPostedObject = null;
    private final List<String> mPostedPaths = new CopyOnWriteArrayList<String>();
    private volatile JSONObject jsonResponseObject = null;
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
        jsonResponseObject = jso;
    }

    public void setException(ConnectionException exception) {
        this.exception = exception;
    }
    
    @Override
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        onRequest("postRequestWithObject", path);
        mPostedObject = jso;
        throwExceptionIfSet();
        return jsonResponseObject;
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
        return jsonResponseObject;
    }

    private void onRequest(String method, String path) {
        mPostedCounter++;
        MyLog.v(this, method + " num:" + mPostedCounter + "; path:'" + path +"', host:'" 
        + data.originUrl + "', instanceId:" + mInstanceId );
        MyLog.v(this, Arrays.toString(Thread.currentThread().getStackTrace()));
        mPostedPaths.add(path);
        networkDelay();
    }

    @Override
    protected JSONObject getRequest(String path) throws ConnectionException {
        return getRequestInner("getRequest", path);
    }

    private JSONObject getRequestInner(String method, String path) throws ConnectionException {
        onRequest(method, path);
        throwExceptionIfSet();
        return jsonResponseObject;
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
        return mPostedObject;
    }

    public List<String> getPostedPaths() {
        return mPostedPaths;
    }

    public String substring2PostedPath(String substringToSearch) {
        String found = "";
        for (String path : mPostedPaths) {
            if (path.contains(substringToSearch)) {
                found = path;
                break;
            }
        }
        return found;
    }
    
    public long getPostedCounter() {
        return mPostedCounter;
    }

    public void clearPostedData() {
        mPostedCounter = 0;
        mPostedObject = null;
        mPostedPaths.clear();
    }

    public long getInstanceId() {
        return mInstanceId;
    }

    @Override
    public HttpConnection getNewInstance() {
        return this;
    }

    @Override
    public void downloadFile(String url, File file) {
        // Empty
    }
}
