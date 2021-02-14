/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import android.net.Uri;

import org.andstatus.app.net.http.ConnectionException;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import io.vavr.control.Try;

public final class UrlUtils {
    private static final String TAG = UrlUtils.class.getSimpleName();
    // From http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address?rq=1
    private static final String validHostnameRegex = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$";
    private static final Pattern validHostnameRegexPattern = Pattern.compile(validHostnameRegex);
    public static final URL MALFORMED = fromString("http://127.0.0.1/malformedUrl");

    private UrlUtils() {
        // Empty
    }

    public static Optional<String> getHost(String strUrl) {
        return Optional.ofNullable(fromString(strUrl)).map(URL::getHost).filter(UrlUtils::hostIsValid);
    }

    public static boolean hostIsValid(String host) {
        return !StringUtil.isEmpty(host) && validHostnameRegexPattern.matcher(host).matches();
    }

    public static boolean hasHost(URL url) {
        return url != null && hostIsValid(url.getHost());
    }

    public static boolean isHostOnly(URL url) {
        return url != null && StringUtil.isEmpty(url.getFile())
                && url.getHost().contentEquals(url.getAuthority());
    }

    public static URL fromString(String strUrl) {
        if (StringUtil.isEmpty(strUrl)) return null;

        try {
            return new URL(strUrl);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public static URL fromUri(Uri uri) {
        if (uri == null || uri == Uri.EMPTY) {
            return null;
        } else {
            return fromString(uri.toString());
        }
    }
    
    public static URL fromJson(JSONObject jso, String urlTag) throws JSONException {
        if (jso != null && !StringUtil.isEmpty(urlTag) && jso.has(urlTag)) {
            String strUrl = jso.getString(urlTag);
            try {
                return new URL(strUrl);
            } catch (MalformedURLException e) {
                MyLog.d(TAG, "tag:'" + urlTag + "' has malformed URL:'" + strUrl + "'", e);
            }
        } 
        return null;
    }

    public static URL buildUrl(String hostOrUrl, boolean isSsl) {
        if (StringUtil.isEmpty(hostOrUrl)) {
            return null;
        }
        String corrected = correctedHostOrUrl(hostOrUrl); 
        if (hostIsValid(corrected)) {
            return fromString("http" + (isSsl ? "s" : "") + "://" + corrected);
        }
        URL urlIn = fromString(corrected);
        if (urlIn == null || urlIn.getProtocol().equals(isSsl ? "https" : "http")) {
            return urlIn;
        }
        return fromString( (isSsl ? "https" : "http") + urlIn.toExternalForm().substring(urlIn.toExternalForm().indexOf(":")));
    }

    private static String correctedHostOrUrl(String hostOrUrl) {
        if (StringUtil.isEmpty(hostOrUrl)) {
            return "";
        }
        // Test with: http://www.regexplanet.com/advanced/java/index.html
        return hostOrUrl.replaceAll(" ","").toLowerCase(Locale.ENGLISH);
    }
    
    public static Try<String> pathToUrlString(URL originUrl, String path, boolean failOnInvalid) {
        Try<URL> url = pathToUrl(originUrl, path);
        if (url.isFailure()) {
            return failOnInvalid
                ? Try.failure(ConnectionException.hardConnectionException("URL is unknown or malformed. System URL:'"
                    + originUrl + "', path:'" + path + "'", null))
                : Try.success("");
        }
        String host = url.map(URL::getHost).getOrElse("");
        if (failOnInvalid && (host.equals("example.com") || host.endsWith(".example.com"))) {
            return Try.failure(ConnectionException.fromStatusCode(ConnectionException.StatusCode.NOT_FOUND,
                    "URL: '" + url.get().toExternalForm() + "'"));
        }
        return url.map(URL::toExternalForm);
    }

    public static Try<URL> pathToUrl(URL originUrl, String path) {
        try {
            if (path != null && path.contains("://")) {
                return Try.success(new URL(path));
            }
            return Try.success(new URL(originUrl, path));
        } catch (MalformedURLException e) {
            return TryUtils.failure("Malformed URL, originUrl:'" + originUrl + "', path:'" + path + "'");
        }
    }

}
