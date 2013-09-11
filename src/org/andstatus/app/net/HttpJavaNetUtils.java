package org.andstatus.app.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

class HttpJavaNetUtils {
    static String encode(Map<String, String> params) {
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
        } catch(UnsupportedEncodingException ex) {
            throw new RuntimeException(ex);
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

}
