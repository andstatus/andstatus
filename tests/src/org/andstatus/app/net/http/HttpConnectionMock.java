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

package org.andstatus.app.net.http;

import android.text.TextUtils;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpConnectionMock extends HttpConnection {
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("HttpConnectionMock [");
        builder.append("Requests sent: " + getRequestsCounter());
        builder.append("; Data posted " + getPostedCounter() + " times");
        builder.append("; results=");
        builder.append(Arrays.toString(getResults().toArray()));
        builder.append(", responseString=");
        builder.append(responseString);
        builder.append(", exception=");
        builder.append(exception);
        builder.append(", password=");
        builder.append(password);
        builder.append(", userToken=");
        builder.append(userToken);
        builder.append(", userSecret=");
        builder.append(userSecret);
        builder.append(", networkDelayMs=");
        builder.append(networkDelayMs);
        builder.append(", mInstanceId=");
        builder.append(mInstanceId);
        builder.append("]");
        return builder.toString();
    }

    private final List<HttpReadResult> results = new CopyOnWriteArrayList<HttpReadResult>();
    private volatile String responseString = "";
    private volatile ConnectionException exception = null;

    private volatile String password = "password";
    private volatile String userToken = "token";
    private volatile String userSecret = "secret";
    
    private volatile long networkDelayMs = 1000;
    protected final long mInstanceId = InstanceId.next(); 
    
    public HttpConnectionMock() {
        MyLog.v(this, "Created, instanceId:" + mInstanceId);
    }
    
    public void setResponse(String responseString) {
        this.responseString = responseString;
    }

    public void setException(ConnectionException exception) {
        this.exception = exception;
    }
    
    @Override
    protected void postRequest(HttpReadResult result) throws ConnectionException {
        onRequest("postRequestWithObject", result);
        throwExceptionIfSet();
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

    private void onRequest(String method, HttpReadResult result) {
        result.strResponse = responseString;
        results.add(result);
        MyLog.v(this, method + " num:" + results.size() + "; path:'" + result.getUrl() +"', host:'" 
        + data.originUrl + "', instanceId:" + mInstanceId );
        MyLog.v(this, Arrays.toString(Thread.currentThread().getStackTrace()));
        networkDelay();
    }

    private void getRequestInner(String method, HttpReadResult result) throws ConnectionException {
        onRequest(method, result);
        throwExceptionIfSet();
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

    public JSONObject getPostedJSONObject() throws ConnectionException {
        return results.get(results.size()-1).getFormParams();
    }

    public List<HttpReadResult> getResults() {
        return results;
    }

    public String substring2PostedPath(String substringToSearch) {
        String found = "";
        for (HttpReadResult result : getResults()) {
            if (result.getUrl().contains(substringToSearch)) {
                found = result.getUrl();
                break;
            }
        }
        return found;
    }
    
    public int getRequestsCounter() {
        return results.size();
    }
    
    public int getPostedCounter() {
        int count = 0;
        for (HttpReadResult result : getResults()) {
            if (result.hasFormParams()) {
                count++;
            }
        }
        return count;
    }

    public void clearPostedData() {
        results.clear();
    }

    public long getInstanceId() {
        return mInstanceId;
    }

    @Override
    public HttpConnection getNewInstance() {
        return this;
    }

    @Override
    protected void getRequest(HttpReadResult result) throws ConnectionException {
        getRequestInner("getRequest", result);
    }
}
