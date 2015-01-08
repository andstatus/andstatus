/*
 * Copyright (C) 2013-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.data.DbUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public class HttpConnectionUtils {
    public static final String UTF_8 = "UTF-8";

    private HttpConnectionUtils() {
    }
    
    static String encode(Map<String, String> params) throws ConnectionException {
        try {
            StringBuilder sb = new StringBuilder();
            for(Map.Entry<String, String> entry : params.entrySet()) {
                if(sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(URLEncoder.encode(entry.getKey(), UTF_8));
                sb.append('=');
                sb.append(URLEncoder.encode(entry.getValue(), UTF_8));
            }
            
            return sb.toString();
        } catch(UnsupportedEncodingException e) {
            throw new ConnectionException("Encoding params", e);
        }
    }

    private static final int BUFFER_LENGTH = 4096;
    static String readStreamToString(InputStream in) throws IOException {
        char[] buffer = new char[BUFFER_LENGTH];
        int count;
        StringBuilder builder = new StringBuilder();
        try {
            Reader reader = new InputStreamReader(in, UTF_8);
            try {
                while((count = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, count);
                }
            } finally {
                DbUtils.closeSilently(reader);
            }
        } finally {
            DbUtils.closeSilently(in);
        }
        return builder.toString();
    }

    public static void readStreamToFile(InputStream in, File file) throws IOException {
        byte[] buffer = new byte[BUFFER_LENGTH];
        int count;
        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            try {
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            } finally {
                DbUtils.closeSilently(out);
            }
        } finally {
            DbUtils.closeSilently(in);
        }
    }
    
}
