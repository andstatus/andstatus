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

package org.andstatus.app.net.http;

import android.text.format.Formatter;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.vavr.control.Try;

public class HttpConnectionUtils {
    private static final String UTF_8 = "UTF-8";
    private static final int BUFFER_LENGTH = 4096;

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

    public static Try<HttpReadResult> readStream(HttpReadResult result, InputStream in) throws IOException {
        if (in == null) {
            return Try.failure(ConnectionException.fromStatusCode(ConnectionException.StatusCode.CLIENT_ERROR, "Input stream is null"));
        }
        return result.fileResult == null
                ? readStreamToString(result, in)
                : readStreamToFile(result, in);
    }

    private static Try<HttpReadResult> readStreamToString(HttpReadResult resultIn, InputStream in) throws IOException {
        char[] buffer = new char[BUFFER_LENGTH];
        ReadChecker checker = new ReadChecker(resultIn);
        StringBuilder builder = new StringBuilder();
        int count;
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            while ((count = reader.read(buffer)) != -1) {
                if (checker.isFailed(count)) return resultIn.toFailure();
                builder.append(buffer, 0, count);
            }
        } finally {
            DbUtils.closeSilently(in);
        }
        resultIn.strResponse = builder.toString();
        return Try.success(resultIn);
    }

    private static Try<HttpReadResult> readStreamToFile(HttpReadResult resultIn, InputStream in) throws IOException {
        byte[] buffer = new byte[BUFFER_LENGTH];
        ReadChecker checker = new ReadChecker(resultIn);
        int count;
        try (FileOutputStream fileOutputStream = new FileOutputStream(resultIn.fileResult);
             OutputStream out = new BufferedOutputStream(fileOutputStream)) {
            while ((count = in.read(buffer)) != -1) {
                if (checker.isFailed(count)) return resultIn.toFailure();
                out.write(buffer, 0, count);
            }
        } finally {
            DbUtils.closeSilently(in);
        }
        return Try.success(resultIn);
    }

    private static class ReadChecker {
        final HttpReadResult result;
        long size = 0;

        ReadChecker(HttpReadResult result) {
            this.result = result;
        }

        boolean isFailed(int count) {
            size += count;
            if (size > result.maxSizeBytes) {
                result.setException(ConnectionException.hardConnectionException(
                        "File, downloaded from \"" + result.getUrl() + "\", is too large: at least "
                                + Formatter.formatShortFileSize(MyContextHolder.get().context(), size),
                        null));
                return true;
            }
            return false;
        }

    }
}
