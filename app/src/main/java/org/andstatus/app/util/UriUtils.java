/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.service.ConnectionState;
import org.json.JSONObject;

import java.net.URL;

public class UriUtils {
    public static final String TEMP_OID_PREFIX = "andstatustemp:";

    private UriUtils() {
        // Empty
    }

    @NonNull
    public static Uri fromAlternativeTags(JSONObject jso, String tag1, String tag2) {
        Uri uri = fromJson(jso, tag1);
        if (isEmpty(uri)) {
            uri = fromJson(jso, tag2);
        }
        return uri;
    }

    public static boolean nonEmpty(Uri uri) {
        return !isEmpty(uri);
    }

    /** @return true for null also */
    public static boolean isEmpty(Uri uri) {
        return uri == null || Uri.EMPTY.equals(uri);
    }

    @NonNull
    public static Uri fromJson(JSONObject jsoIn, String pathIn) {
        if (jsoIn == null || TextUtils.isEmpty(pathIn)) return Uri.EMPTY;

        String[] path = pathIn.split("/");
        JSONObject jso = path.length == 2 ? jsoIn.optJSONObject(path[0]) : jsoIn;
        String urlTag = path.length == 2 ? path[1] : pathIn;
        if (jso != null && !TextUtils.isEmpty(urlTag) && jso.has(urlTag)) {
            return fromString(jso.optString(urlTag));
        }
        return Uri.EMPTY;
    }

    @NonNull
    public static Uri fromString(String strUri) {
        return SharedPreferencesUtil.isEmpty(strUri) ? Uri.EMPTY : Uri.parse(strUri.trim());
    }

    @NonNull
    public static Uri notNull(Uri uri) {
        return uri == null ? Uri.EMPTY : uri;
    }

    @NonNull
    public static Uri fromUrl(URL url) {
        if (url == null) {
            return Uri.EMPTY;
        } else {
            return fromString(url.toExternalForm());
        }
    }
    
    /** See http://developer.android.com/guide/topics/providers/document-provider.html */
   public static int flagsToTakePersistableUriPermission() {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        flags = flags | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        return flags;
    }
    
    /** See http://stackoverflow.com/questions/25999886/android-content-provider-uri-doesnt-work-after-reboot */
    public static void takePersistableUriPermission(Context context, Uri uri, int takeFlagsIn) {
        if ((takeFlagsIn & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
            final int takeFlags = takeFlagsIn & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                MyLog.i(context,"Exception while taking persistable URI permission for '" + uri + "'", e);
            }
        } else {
            MyLog.i(context,"No persistable URI permission for '" + uri + "'");
        }
    }

    public static boolean isDownloadable(Uri uri) {
        if (uri != null) {
            String scheme = uri.getScheme();
            if (scheme != null) {
                switch (scheme) {
                    case "http":
                    case "https":
                        return true;
                    default:
                        break;
                }
            }
        }
        return false;
    }

    public static boolean isRealOid(String oid) {
        return !nonRealOid(oid);
    }

    public static boolean nonRealOid(String oid) {
        return isEmptyOid(oid) || isTempOid(oid);
    }

    public static boolean isTempOid(String oid) {
        return nonEmptyOid(oid) && oid.startsWith(TEMP_OID_PREFIX);
    }

    public static boolean nonEmptyOid(String oid) {
        return !isEmptyOid(oid);
    }

    public static boolean isEmptyOid(String oid) {
        return SharedPreferencesUtil.isEmpty(oid);
    }

    /**
     * Based on http://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-timeouts
     */
    public static ConnectionState getConnectionState(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return ConnectionState.UNKNOWN;
        }
        ConnectionState state = ConnectionState.OFFLINE;
        NetworkInfo networkInfoOnline = connectivityManager.getActiveNetworkInfo();
        if (networkInfoOnline == null) {
            return state;
        }
        if (networkInfoOnline.isAvailable() && networkInfoOnline.isConnected()) {
            state = ConnectionState.ONLINE;
        } else {
            return state;
        }
        NetworkInfo networkInfoWiFi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfoWiFi == null) {
            return state;
        }
        if (networkInfoWiFi.isAvailable() && networkInfoWiFi.isConnected()) {
            state = ConnectionState.WIFI;
        }
        return state;
    }
}
