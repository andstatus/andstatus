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
package org.andstatus.app.util

import org.junit.Assert
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun testHostOnly() {
        var host = "example.com"
        var isUrl = false
        assertHostOnly("example.com", host, isUrl)
        isUrl = true
        assertHostOnly("http://example.com", host, isUrl)
        assertHostOnly("https://example.com", host, isUrl)
        assertHostOnly("http://example.com", host, isUrl)
        host = "gs.kawa-kun.com"
        assertHostOnly("http://gs.kawa-kun.com", host, isUrl)
    }

    private fun assertHostOnly(hostOrUrl: String?, host: String?, isUrl: Boolean) {
        var isSsl = true
        Assert.assertEquals(isUrl, !UrlUtils.hostIsValid(hostOrUrl))
        var url = UrlUtils.fromString(hostOrUrl)
        Assert.assertEquals(url != null, isUrl)
        url = UrlUtils.buildUrl(hostOrUrl, isSsl)
        Assert.assertEquals(host, url?.host)
        val strUrl = "https://$host"
        Assert.assertEquals(strUrl, url?.toExternalForm())
        val path = "somepath/somewhere.json"
        Assert.assertEquals("$strUrl/$path", UrlUtils.pathToUrlString(url, path, false)?.get())
        Assert.assertTrue(UrlUtils.isHostOnly(url))
        isSsl = false
        url = UrlUtils.buildUrl(hostOrUrl, isSsl)
        if (!isUrl) {
            Assert.assertEquals(host, url?.host)
        }
        Assert.assertTrue(UrlUtils.isHostOnly(url))
    }

    @Test
    fun testNotOnlyHost() {
        var strUrl = "http://host.org/directory/file.jpg"
        var strUrlSsl = "https://host.org/directory/file.jpg"
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, true)
        strUrl = "http://host.org/"
        strUrlSsl = "https://host.org/"
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, true)
        var strInput = "http://host.org/directory"
        strUrl = "http://host.org/directory"
        strUrlSsl = "https://host.org/directory"
        assertNotOnlyHost(strInput, strUrl, strUrlSsl, true)
        strInput = "http://host.org/directory/"
        strUrl = "http://host.org/directory/"
        strUrlSsl = "https://host.org/directory/"
        assertNotOnlyHost(strInput, strUrl, strUrlSsl, true)
        strUrl = "http://username:password@host.org:8080/directory/file?query#ref"
        strUrlSsl = "https://username:password@host.org:8080/directory/file?query#ref"
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, true)
        strUrl = "http://host.org/directory//file.jpg"
        strUrlSsl = "https://host.org/directory//file.jpg"
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, true)
        strUrl = "http//host.org/directory/file.jpg"
        assertNotOnlyHost(strUrl, strUrl, strUrlSsl, false)
    }

    private fun assertNotOnlyHost(strInput: String?, strUrl: String?, strUrlSsl: String?, isValid: Boolean) {
        if (isValid) {
            Assert.assertEquals(strUrl, UrlUtils.buildUrl(strInput, false)?.toExternalForm())
            Assert.assertEquals(strUrlSsl, UrlUtils.buildUrl(strInput, true)?.toExternalForm())
            Assert.assertEquals(false, UrlUtils.isHostOnly(UrlUtils.buildUrl(strInput, true)))
        } else {
            Assert.assertEquals(null, UrlUtils.buildUrl(strInput, false))
            Assert.assertEquals(false, UrlUtils.isHostOnly(null))
        }
    }

    @Test
    fun testPathToUrl() {
        var strUrl = "https://username:password@host.org:8080/directory/"
        val path = "somepath/somewhere.json"
        var addSlash = false
        assertPath2Url(strUrl, path, addSlash)
        strUrl = "https://host.org"
        addSlash = true
        assertPath2Url(strUrl, path, addSlash)
        strUrl = "https://username:password@host.org"
        assertPath2Url(strUrl, path, addSlash)
    }

    private fun assertPath2Url(strUrl: String?, path: String?, addSlash: Boolean) {
        val isSsl = true
        val url1 = UrlUtils.buildUrl(strUrl, isSsl)
        val strUrl2 = UrlUtils.pathToUrlString(url1, path, false).getOrElse("(failure)")
        Assert.assertEquals(strUrl + (if (addSlash) "/" else "") + path, strUrl2)
    }
}