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

import androidx.annotation.RawRes;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import io.vavr.control.CheckedFunction;
import io.vavr.control.Try;

public class HttpConnectionMock extends HttpConnection {

    private final List<HttpReadResult> results = new CopyOnWriteArrayList<>();
    private final List<String> responses = new CopyOnWriteArrayList<>();
    public volatile int responsesCounter = 0;
    private boolean sameResponse = false;
    private volatile CheckedFunction<Void, InputStream> responseStreamSupplier = null;

    private volatile RuntimeException runtimeException = null;
    private volatile ConnectionException exception = null;

    private volatile String password = "password";
    private volatile String userToken = "token";
    private volatile String userSecret = "secret";
    
    private volatile long networkDelayMs = 1000;
    private final long mInstanceId = InstanceId.next();

    public HttpConnectionMock() {
        MyLog.v(this, "Created, instanceId:" + mInstanceId);
    }

    public void setSameResponse(boolean sameResponse) {
        this.sameResponse = sameResponse;
    }

    @Override
    public boolean errorOnInvalidUrls() {
        return false;
    }

    public void addResponse(@RawRes int responseResourceId) throws IOException {
        addResponse(RawResourceUtils.getString(responseResourceId));
    }

    public void addResponse(String responseString) {
        responses.add(responseString);
    }

    public void setResponseStreamSupplier(CheckedFunction<Void, InputStream> responseStreamSupplier) {
        this.responseStreamSupplier = responseStreamSupplier;
    }

    public void setRuntimeException(RuntimeException exception) {
        runtimeException = exception;
    }

    public void setException(ConnectionException exception) {
        this.exception = exception;
    }

    @Override
    public String pathToUrlString(String path) {
        if (data.originUrl == null) {
            data.originUrl = UrlUtils.buildUrl("mocked.example.com", true);
        }
        return super.pathToUrlString(path);
    }

    @Override
    public HttpReadResult postRequest(HttpReadResult result) {
        onRequest("postRequestWithObject", result);
        setExceptions(result);
        return result;
    }

    private void setExceptions(HttpReadResult result) {
        if (runtimeException != null) {
            result.setException(runtimeException);
        } else if (exception != null) {
            result.setException(exception);
        }
    }

    @Override
    public void setUserTokenWithSecret(String token, String secret) {
        userToken = token;
        userSecret = secret;
    }

    @Override
    public String getUserToken() {
        return userToken;
    }

    @Override
    public String getUserSecret() {
        return userSecret;
    }

    private void onRequest(String method, HttpReadResult result) {
        result.strResponse = getNextResponse();
        if (responseStreamSupplier != null) {
            result.readStream("", responseStreamSupplier);
        }
        results.add(result);
        MyLog.v(this, method + " num:" + results.size() + "; path:'" + result.getUrl()
                + "', originUrl:'" + data.originUrl + "', instanceId:" + mInstanceId );
        MyLog.v(this, Arrays.toString(Thread.currentThread().getStackTrace()));
        DbUtils.waitMs("networkDelay", Math.toIntExact(networkDelayMs));
    }

    private synchronized String getNextResponse() {
        return sameResponse
                ? (responses.isEmpty() ? "" : responses.get(responses.size() - 1))
                : (responsesCounter < responses.size() ? responses.get(responsesCounter++) : "");
    }

    private HttpReadResult getRequestInner(String method, HttpReadResult result) {
        onRequest(method, result);
        setExceptions(result);
        return result;
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
        return !StringUtil.isEmpty(password) || ( !TextUtils.isDigitsOnly(userToken) && !StringUtil.isEmpty(userSecret));
    }

    public JSONObject getPostedJSONObject() {
        return results.get(results.size()-1).request.postParams.orElse(new JSONObject());
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

    public List<JSONObject> getPostedObjects() {
        return getResults().stream()
                .map(r -> r.request.postParams.orElse(null))
                .collect(Collectors.toList());
    }

    public int getPostedCounter() {
        return getResults().stream().reduce(0,
                (a, r) -> r.request.postParams.map(p -> a + 1).orElse(a),
                (a1, a2) -> a1 + a2);
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
    public HttpReadResult getRequest(HttpReadResult result) {
        return getRequestInner("getRequest", result);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("HttpConnectionMock [");
        builder.append("Requests sent: " + getRequestsCounter());
        builder.append("; Data posted " + getPostedCounter() + " times");
        builder.append("\nSent " + responsesCounter + " responses");
        if (results.size() > 0) {
            builder.append("\nresults:" + results.size());
            results.forEach(r -> builder.append("\nResult: " + r.toString()));
        }
        if (responses.size() > 0) {
            builder.append("\n\nresponses:" + responses.size());
            responses.forEach(r -> builder.append("\nResponse: " + r.toString()));
        }
        if (exception != null) {
            builder.append("\nexception=");
            builder.append(exception);
        }
        builder.append("\npassword=");
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
}
