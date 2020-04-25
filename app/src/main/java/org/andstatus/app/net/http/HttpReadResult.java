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

import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vavr.control.CheckedFunction;
import io.vavr.control.Try;

import static org.andstatus.app.util.TryUtils.checkException;

public class HttpReadResult {
    public final HttpRequest request;
    private String urlString = "";
    private URL url;
    private Map<String, List<String>> headers = Collections.emptyMap();
    boolean redirected = false;
    private Optional<String> location = Optional.empty();
    boolean retriedWithoutAuthentication = false;

    private StringBuilder logBuilder =  new StringBuilder();
    private Exception exception = null;
    String strResponse = "";
    String statusLine = "";
    private int intStatusCode = 0;
    private StatusCode statusCode = StatusCode.UNKNOWN;

    public HttpReadResult(HttpRequest request) {
        this.request = request;
        setUrl(request.uri.toString());
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    <T> HttpReadResult setHeaders(Stream<T> headers, Function<T, String> keyMapper, Function<T, String> valueMapper) {
        return setHeaders(headers.collect(toHeaders(keyMapper, valueMapper)));
    }

    private HttpReadResult setHeaders(@NonNull Map<String, List<String>> headers) {
        // Header field names are case-insensitive, see https://stackoverflow.com/a/5259004/297710
        Map<String, List<String>> lowercaseKeysMap = new HashMap<>();
        headers.entrySet().forEach(entry -> {
            lowercaseKeysMap.put(entry.getKey() == null ? null : entry.getKey().toLowerCase(), entry.getValue());
        });
        this.headers = lowercaseKeysMap;
        this.location = Optional.ofNullable(this.headers.get("location"))
                .orElse(Collections.emptyList()).stream()
                .filter(StringUtil::nonEmpty)
                .findFirst()
                .map(l -> l.replace("%3F", "?"));
        return this;
    }

    private static <T> Collector<T, ?, Map<String, List<String>>> toHeaders(
            Function<T, String> fieldNameMapper,
            Function<T, String> valueMapper) {
        return Collectors.toMap(
                fieldNameMapper,
                v -> Collections.singletonList(valueMapper.apply(v)),
                (a, b) -> {
                    List<String> out = new ArrayList<>(a);
                    out.addAll(b);
                    return out;
                });
    }

    public Optional<String> getLocation() {
        return location;
    }

    public final HttpReadResult setUrl(String urlIn) {
        if (!StringUtil.isEmpty(urlIn) && !urlString.contentEquals(urlIn)) {
            urlString = urlIn;
            try {
                url = new URL(urlIn);
            } catch (MalformedURLException e) {
                url = UrlUtils.MALFORMED;
            }
        }
        return this;
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
                + ((statusCode == StatusCode.OK) || StringUtil.isEmpty(statusLine)
                        ? "" : "; statusLine:'" + statusLine + "'")
                + (intStatusCode == 0 ? "" : "; statusCode:" + statusCode + " (" + intStatusCode + ")")
                + (redirected ? "; redirected" : "")
                + "; url:'" + urlString + "'"
                + (retriedWithoutAuthentication ? "; retried without auth" : "")
                + (StringUtil.isEmpty(strResponse) ? "" : "; response:'" + I18n.trimTextAt(strResponse, 40) + "'")
                + location.map(str -> "; location:'" + str + "'").orElse("")
                + (exception == null ? "" : "; \nexception: " + exception.toString())
                + "\nRequested: " + request;
    }

    public Try<JSONArray> getJsonArrayInObject(String arrayName) {
        String method = "getRequestArrayInObject";
        return getJsonObject()
        .flatMap(jso -> {
            JSONArray jArr = null;
            if (jso != null) {
                try {
                    jArr = jso.getJSONArray(arrayName);
                } catch (JSONException e) {
                    return Try.failure(ConnectionException.loggedJsonException(this, method + ", arrayName=" + arrayName, e, jso));
                }
            }
            return Try.success(jArr);
        });
    }

    public Try<JSONObject> getJsonObject() {
        return innerGetJsonObject(strResponse);
    }

    public String getResponse() {
        return strResponse;
    }

    private Try<JSONObject> innerGetJsonObject(String strJson) {
        String method = "getJsonObject; ";
        JSONObject jso = null;
        try {
            if (StringUtil.isEmpty(strJson)) {
                jso = new JSONObject();
            } else {
                jso = new JSONObject(strJson);
                String error = JsonUtils.optString(jso, "error");
                if ("Could not authenticate you.".equals(error)) {
                    appendToLog("error:" + error);
                    return Try.failure(new ConnectionException(toString()));
                }
                
            }
        } catch (JSONException e) {
            return Try.failure(ConnectionException.loggedJsonException(this, method + I18n.trimTextAt(toString(), 500), e, strJson));
        }
        return Try.success(jso);
    }

    public Try<JSONArray> getJsonArray() {
        return getJsonArray("items");
    }

    Try<JSONArray> getJsonArray(String arrayKey) {
        String method = "getJsonArray; ";
        if (StringUtil.isEmpty(strResponse)) {
            MyLog.v(this, () -> method + "; response is empty");
            return Try.success(new JSONArray());
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
                        return Try.failure(ConnectionException.loggedJsonException(this, "'" + arrayKey + "' is not an array?!"
                                + method + toString(), e, jso));
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
            return Try.failure(ConnectionException.loggedJsonException(this, method + toString(), e, strResponse));
        } catch (Exception e) {
            return Try.failure(ConnectionException.loggedHardJsonException(this, method + toString(), e, strResponse));
        }
        return Try.success(jsa);
    }
 
    ConnectionException getExceptionFromJsonErrorResponse() {
        StatusCode statusCode = this.statusCode;
        String error = "?";
        if (TextUtils.isEmpty(strResponse)) {
            return new ConnectionException(statusCode, "Empty response; " + toString());
        }
        try {
            JSONObject jsonError = new JSONObject(strResponse);
            error = JsonUtils.optString(jsonError,"error", error);
            if (statusCode == StatusCode.UNKNOWN && error.contains("not found")) {
                statusCode = StatusCode.NOT_FOUND;
            }
            return new ConnectionException(statusCode, "Error='" + error + "'; " + toString());
        } catch (JSONException e) {
            return new ConnectionException(statusCode, toString());
        }
    }

    public HttpReadResult setException(Throwable e) {
        checkException(e);
        if (exception == null) {
            exception = e instanceof Exception
                    ? (Exception) e
                    : new ConnectionException("Unexpected exception", e);
        }
        return this;
    }

    public Exception getException() {
        return exception;
    }

    public Try<HttpReadResult> tryToParse() {
        if (exception instanceof ConnectionException) {
            return Try.failure(exception);
        }
        if ( isStatusOk()) {
            if (request.isFileTooLarge()) {
                setException(ConnectionException.hardConnectionException(
                    "File, downloaded from \"" + urlString + "\", is too large: "
                      + Formatter.formatShortFileSize(MyContextHolder.get().context(), request.fileResult.length()),
                    null));
                return Try.failure(exception);
            }
            MyLog.v(this, this::toString);
        } else {
            if (!StringUtil.isEmpty(strResponse)) {
                setException(getExceptionFromJsonErrorResponse());
            } else {
                setException(ConnectionException.fromStatusCodeAndThrowable(statusCode, toString(), exception));
            }
            return Try.failure(exception);
        }
        return Try.success(this);
    }

    public boolean isStatusOk() {
        return exception == null && (statusCode == StatusCode.OK || statusCode == StatusCode.UNKNOWN);
    }

    Try<HttpReadResult> toFailure() {
        return Try.failure(ConnectionException.from(this));
    }

    HttpReadResult logResponse(HttpConnectionData connectionData) {
        if (strResponse != null && MyPreferences.isLogNetworkLevelMessages()) {
            Object objTag = "response";
            MyLog.logNetworkLevelMessage(objTag, connectionData.getLogName(request), strResponse,
                    MyStringBuilder.of("")
                            .atNewLine("logger-URL", urlString)
                            .atNewLine("logger-account", connectionData.getAccountName().getName())
                            .atNewLine("logger-authenticated",
                                    Boolean.toString(!retriedWithoutAuthentication && request.authenticate))
                            .apply(this::appendHeaders).toString());
        }
        return this;
    }

    MyStringBuilder appendHeaders(MyStringBuilder builder) {
        builder.atNewLine("Headers:");
        for (Map.Entry<String, List<String>> header: getHeaders().entrySet()) {
            builder.atNewLine(header.getKey(), header.getValue().toString());
        }
        return builder;
    }

    void onRetryWithoutAuthentication() {
        retriedWithoutAuthentication = true;
        appendToLog("Retrying without authentication" + (exception == null ? "" : ", exception: " + exception));
        exception = null;
        MyLog.v(this, this::toString);
    }

    public Try<HttpReadResult> readStream(String msgLog, CheckedFunction<Void, InputStream> supplier) {
        return HttpConnectionUtils.readStream(this, msgLog, supplier);
    }
}