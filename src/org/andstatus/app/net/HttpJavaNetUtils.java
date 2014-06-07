/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyPreferences;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Map;

public class HttpJavaNetUtils {

    private HttpJavaNetUtils() {
    }
    
    static String encode(Map<String, String> params) throws ConnectionException {
        try {
            StringBuilder sb = new StringBuilder();
            for(Map.Entry<String, String> entry : params.entrySet()) {
                if(sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
            
            return sb.toString();
        } catch(UnsupportedEncodingException e) {
            throw new ConnectionException("Encoding params", e);
        }
    }

    static String readAll(InputStream s) throws IOException {
        return readAll(new InputStreamReader(s, "UTF-8"));
    }
    
    static String readAll(Reader r) throws IOException {
        int nRead;
        char[] buf = new char[16 * 1024];
        StringBuilder bld = new StringBuilder();
        while((nRead = r.read(buf)) != -1) {
            bld.append(buf, 0, nRead);
        }
        return bld.toString();
    }

    public static InputStream urlOpenStream(URL url) throws IOException {
        URLConnection con = url.openConnection();
        con.setConnectTimeout(MyPreferences.getConnectionTimeoutMs());
        con.setReadTimeout(MyPreferences.getConnectionTimeoutMs());
        InputStream is = con.getInputStream();
        return is;
    }
}
