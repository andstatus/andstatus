/* Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.format.Formatter;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Optional;

import io.vavr.control.Try;

public class HttpReadResult {
    private final Uri uriInitial;
    private String urlString = "";
    private URL url;
    boolean authenticate = true;
    private boolean mIsLegacyHttpProtocol = false;
    public final long maxSizeBytes;

    public final Optional<JSONObject> formParams;
    private StringBuilder logBuilder =  new StringBuilder();
    private Exception exception = null;
    String strResponse = "";
    public final File fileResult;
    String statusLine = "";
    private int intStatusCode = 0;
    private StatusCode statusCode = StatusCode.UNKNOWN;

    boolean redirected = false;

    public HttpReadResult(Uri uriIn, JSONObject formParams) throws ConnectionException {
        this (uriIn, null, formParams);
    }

    public HttpReadResult(Uri uriIn, File file, JSONObject formParams) throws ConnectionException {
        uriInitial = uriIn;
        fileResult = file;
        this.formParams = formParams == null || formParams.length() == 0
            ? Optional.empty()
            : Optional.of(formParams);
        setUrl(uriIn.toString());
        maxSizeBytes = MyPreferences.getMaximumSizeOfAttachmentBytes();
    }

    public final void setUrl(String urlIn) {
        if (!StringUtils.isEmpty(urlIn) && !urlString.contentEquals(urlIn)) {
            redirected = !StringUtils.isEmpty(urlString);
            urlString = urlIn;
            try {
                url = new URL(urlIn);
            } catch (MalformedURLException e) {
                setException(new ConnectionException("Malformed URL; " + toString(), e));
                url = UrlUtils.MALFORMED;
            }
        }
    }
    
    void setStatusCode(int intStatusCodeIn) {
        intStatusCode = intStatusCodeIn;
        statusCode = StatusCode.fromResponseCode(intStatusCodeIn);
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }
    
    public String getUrl() {
        return urlString;
    }

    URL getUrlObj() {
        return url;
    }

    void appendToLog(CharSequence chars) {
        if (TextUtils.isEmpty(chars)) {
            return;
        }
        if (logBuilder.length() > 0) {
            logBuilder.append("; ");
        }
        logBuilder.append(chars);
    }
    
    public String logMsg() {
        return logBuilder.toString();
    }
    
    @Override
    public String toString() {
        return logMsg()
                + ((statusCode == StatusCode.OK) || StringUtils.isEmpty(statusLine)
                        ? "" : "; statusLine:'" + statusLine + "'")
                + (intStatusCode == 0 ? "" : "; statusCode:" + statusCode + " (" + intStatusCode + ")") 
                + "; url:'" + urlString + "'"
                + (isLegacyHttpProtocol() ? "; legacy HTTP" : "")
                + (authenticate ? "; authenticated" : "")
                + (redirected ? "; redirected from:'" + uriInitial + "'" : "")
                + formParams.map(params -> "; posted:'" + params + "'").orElse("")
                + (StringUtils.isEmpty(strResponse) ? "" : "; response:'" + I18n.trimTextAt(strResponse, 40) + "'")
                + (exception == null ? "" : "; \nexception: " + exception.toString())
                + (fileResult == null ? "" : "; saved to file");
    }
    
    JSONObject getJsonObject() throws ConnectionException {
        return innerGetJsonObject(strResponse);
    }

    private JSONObject innerGetJsonObject(String strJson) throws ConnectionException {
        String method = "getJsonObject; ";
        JSONObject jso = null;
        try {
            if (StringUtils.isEmpty(strJson)) {
                jso = new JSONObject();
            } else {
                jso = new JSONObject(strJson);
                String error = jso.optString("error");
                if ("Could not authenticate you.".equals(error)) {
                    appendToLog("error:" + error);
                    throw new ConnectionException(toString());
                }
                
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, method + I18n.trimTextAt(toString(), 500), e, strJson);
        }
        return jso;
    }

    JSONArray getJsonArray(String arrayKey) throws ConnectionException {
        String method = "getJsonArray; ";
        if (StringUtils.isEmpty(strResponse)) {
            MyLog.v(this, () -> method + "; response is empty");
            return new JSONArray();
        }
        JSONTokener jst = new JSONTokener(strResponse);
        JSONArray jsa = null;
        try {
            Object obj = jst.nextValue();
            if (obj instanceof JSONObject) {
                JSONObject jso = (JSONObject) obj;
                if (jso.has(arrayKey)) {
                    try {
                        obj = jso.getJSONArray(arrayKey);
                    } catch (JSONException e) {
                        throw ConnectionException.loggedJsonException(this, "'" + arrayKey + "' is not an array?!"
                                + method + toString(), e, jso);
                    }
                } else {
                    Iterator<String> iterator =  jso.keys();
                    while (iterator.hasNext()) {
                        String key = iterator.next();
                        Object obj2 = jso.get(key);                    
                        if (obj2 instanceof JSONArray) {
                            MyLog.v(this, () -> method + "; found array inside '" + key + "' object");
                            obj = obj2;
                            break;
                        }
                    }
                }
            }
            jsa = (JSONArray) obj;
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, method + toString(), e, strResponse);
        } catch (ClassCastException e) {
            throw ConnectionException.loggedHardJsonException(this, method + toString(), e, strResponse);
        }
        return jsa;
    }
 
    ConnectionException getExceptionFromJsonErrorResponse() {
        StatusCode statusCode = this.statusCode;
        String error = "?";
        if (TextUtils.isEmpty(strResponse)) {
            return new ConnectionException(statusCode, "Empty response");
        }
        try {
            JSONObject jsonError = new JSONObject(strResponse);
            error = jsonError.optString("error", error);
            if (statusCode == StatusCode.UNKNOWN && error.contains("not found")) {
                statusCode = StatusCode.NOT_FOUND;
            }
            return new ConnectionException(statusCode, toString() + "; error='" + error + "'");
        } catch (JSONException e) {
            return new ConnectionException(statusCode, "Response: \"" + strResponse + "\"");
        }
    }

    public void setException(Exception e) {
        exception = e;
    }

    public Exception getException() {
        return exception;
    }

    void parseAndThrow() throws ConnectionException {
        if (exception instanceof ConnectionException) {
            throw (ConnectionException) exception;
        }
        if ( isStatusOk()) {
            if (fileResult != null && fileResult.isFile() && fileResult.exists()
                    && fileResult.length() > MyPreferences.getMaximumSizeOfAttachmentBytes()) {
                throw ConnectionException.hardConnectionException(
                        "File, downloaded from \"" + urlString + "\", is too large: "
                          + Formatter.formatShortFileSize(MyContextHolder.get().context(), fileResult.length()),
                        null);
            }
            MyLog.v(this, this::toString);
        } else {
            if (!StringUtils.isEmpty(strResponse)) {
                throw getExceptionFromJsonErrorResponse();
            } else {
                throw ConnectionException.fromStatusCodeAndThrowable(statusCode, toString(), exception);
            }
        }
    }

    private boolean isStatusOk() {
        return exception == null && (statusCode == StatusCode.OK || statusCode == StatusCode.UNKNOWN);
    }

    boolean isLegacyHttpProtocol() {
        return mIsLegacyHttpProtocol;
    }

    HttpReadResult setLegacyHttpProtocol(boolean mIsLegacyHttpProtocol) {
        this.mIsLegacyHttpProtocol = mIsLegacyHttpProtocol;
        return this;
    }

    void onNoLocationHeaderOnMoved() {
        redirected = true;
        setException(new IllegalArgumentException("No 'Location' header on MOVED response"));
    }

    public Try<HttpReadResult> toFailure() {
        return Try.failure(ConnectionException.from(this));
    }
}