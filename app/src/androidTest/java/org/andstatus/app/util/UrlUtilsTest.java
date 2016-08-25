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

package org.andstatus.app.util;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.Travis;
import org.andstatus.app.net.http.ConnectionException;

import java.net.URL;

@Travis
public class UrlUtilsTest extends InstrumentationTestCase {

    public void testHostOnly() throws ConnectionException {
        String host = "example.com";
        boolean isUrl = false;
        assertHostOnly("example.com", host, isUrl);
        isUrl = true;
        assertHostOnly("http://example.com", host, isUrl);
        assertHostOnly("https://example.com", host, isUrl);
        assertHostOnly("http://example.com", host, isUrl);
        host = "gs.kawa-kun.com";
        assertHostOnly("http://gs.kawa-kun.com", host, isUrl);
    }

    private void assertHostOnly(String hostOrUrl, String host, boolean isUrl) throws ConnectionException {
        boolean isSsl = true;
        assertEquals(isUrl, !UrlUtils.hostIsValid(hostOrUrl));
        URL url = UrlUtils.fromString(hostOrUrl);
        assertEquals(url != null, isUrl);
        url = UrlUtils.buildUrl(hostOrUrl, isSsl);
        assertEquals(host, url.getHost());
        String strUrl = "https://" + host;
        assertEquals(strUrl, url.toExternalForm());
        String path = "somepath/somewhere.json";
        assertEquals(strUrl + "/" + path, UrlUtils.pathToUrlString(url, path, false));
        assertTrue(UrlUtils.isHostOnly(url));
        isSsl = false;
        url = UrlUtils.buildUrl(hostOrUrl, isSsl);
        if (!isUrl) {
            assertEquals(host, url.getHost());
        }
        assertTrue(UrlUtils.isHostOnly(url));
    }

    public void testNotOnlyHost() {
        String strUrl = "http://host.org/directory/file.jpg";
        String strUrlSsl = "https://host.org/directory/file.jpg";
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, true);
        strUrl = "http://host.org/";
        strUrlSsl = "https://host.org/";
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, true);
        String strInput = "http://host.org/directory";
        strUrl = "http://host.org/directory";
        strUrlSsl = "https://host.org/directory";
        assertNotOnlyHost(strInput, strUrl, strUrlSsl, true);
        strInput = "http://host.org/directory/";
        strUrl = "http://host.org/directory/";
        strUrlSsl = "https://host.org/directory/";
        assertNotOnlyHost(strInput, strUrl, strUrlSsl, true);
        strUrl = "http://username:password@host.org:8080/directory/file?query#ref";
        strUrlSsl = "https://username:password@host.org:8080/directory/file?query#ref";
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, true);
        strUrl = "http://host.org/directory//file.jpg";
        strUrlSsl = "https://host.org/directory//file.jpg";
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, true);
        strUrl = "http//host.org/directory/file.jpg";
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, false);
    }

    private void assertNotOnlyHost(String strInput, String strUrl, String strUrlSsl, boolean isValid) {
        if (isValid) {
            assertEquals(strUrl, UrlUtils.buildUrl(strInput, false).toExternalForm());
            assertEquals(strUrlSsl, UrlUtils.buildUrl(strInput, true).toExternalForm());
            assertEquals(UrlUtils.isHostOnly(UrlUtils.buildUrl(strInput, true)), false);
        } else {
            assertEquals(null, UrlUtils.buildUrl(strInput, false));
            assertEquals(UrlUtils.isHostOnly(null), false);
        }
    }

    public void testPathToUrl() throws ConnectionException {
        String strUrl = "https://username:password@host.org:8080/directory/";
        String path = "somepath/somewhere.json";
        boolean addSlash = false;
        assertPath2Url(strUrl, path, addSlash);
        strUrl = "https://host.org";
        addSlash = true;
        assertPath2Url(strUrl, path, addSlash);
        strUrl = "https://username:password@host.org";
        assertPath2Url(strUrl, path, addSlash);
    }

    private void assertPath2Url(String strUrl, String path, boolean addSlash) throws ConnectionException {
        boolean isSsl = true;
        URL url1 = UrlUtils.buildUrl(strUrl, isSsl);
        String strUrl2 = UrlUtils.pathToUrlString(url1, path, false);
        assertEquals(strUrl + (addSlash ? "/" : "") + path, strUrl2);
    }
}
