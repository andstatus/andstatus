/**
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
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public final class UrlUtils {
    private static final String TAG = UrlUtils.class.getSimpleName();

    private UrlUtils() {
        // Empty
    }

    public static boolean hostIsValid(String host) {
        boolean ok = false;
        if (host != null) {
            // From
            // http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address?rq=1
            String validHostnameRegex = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$";
            ok = host.matches(validHostnameRegex);
        }
        return ok;
    }

    public static boolean isHostOnly(URL url) {
        return url != null && TextUtils.isEmpty(url.getFile())
                && url.getHost().contentEquals(url.getAuthority());
    }

    public static URL fromString(String strUrl) {
        if (strUrl != null && !TextUtils.isEmpty(strUrl)) {
            try {
                return new URL(strUrl);
            } catch (MalformedURLException e) {
                MyLog.d(TAG, "Malformed URL:'" + strUrl + "'", e);
            }
        } 
        return null;
    }

    public static URL fromUri(Uri uri) {
        if (uri == null || uri == Uri.EMPTY) {
            return null;
        } else {
            return fromString(uri.toString());
        }
    }
    
    public static URL fromJson(JSONObject jso, String urlTag) throws JSONException {
        if (jso != null && !TextUtils.isEmpty(urlTag) && jso.has(urlTag)) {
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
        if (TextUtils.isEmpty(hostOrUrl)) {
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
        if (TextUtils.isEmpty(hostOrUrl)) {
            return "";
        }
        // Test with: http://www.regexplanet.com/advanced/java/index.html
        return hostOrUrl.replaceAll(" ","").toLowerCase(Locale.ENGLISH);
    }
    
    public static String pathToUrlString(URL originUrl, String path) {
        if (path != null && path.contains("://")) {
            return path;
        } else {
            try {
                return new URL(originUrl, path).toExternalForm();
            } catch (MalformedURLException e) {
                MyLog.d(TAG, "pathToUrl", e);
                return "";
            }
        }
    }
    
}
