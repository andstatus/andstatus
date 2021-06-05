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

import android.net.Uri
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class UriUtilsTest {
    @Test
    fun testIsEmpty() {
        for (uri in EMPTY_URIS) {
            Assert.assertTrue(UriUtils.isEmpty(uri))
        }
        Assert.assertFalse(UriUtils.isEmpty(UriUtils.fromString(".")))
    }

    @Test
    fun testFromString() {
        Assert.assertTrue(UriUtils.fromString(null) != null)
        Assert.assertTrue(UriUtils.fromString("") != null)
        Assert.assertTrue(UriUtils.fromString("something") != null)
    }

    @Test
    fun testNotNull() {
        Assert.assertEquals(Uri.EMPTY, UriUtils.notNull(null))
        val uri = Uri.parse("http://some.org/")
        Assert.assertEquals(UriUtils.notNull(uri), uri)
    }

    @Test
    fun testFromJson() {
        val jso = JSONObject("{\"profile_image_url\":\"http://a0.twimg.com/profile_images/36_normal.jpeg\"" +
                ",\n\"profile_image_url_https\":\"https://si0.twimg.com/profile_images/37_normal.jpeg\"}")
        var uri = UriUtils.fromAlternativeTags(jso, "profile_image_url_https", "profile_image_url")
        Assert.assertEquals("https://si0.twimg.com/profile_images/37_normal.jpeg", uri.toString())
        uri = UriUtils.fromAlternativeTags(jso, "profile_image_url", "profile_image_url_https")
        Assert.assertEquals("http://a0.twimg.com/profile_images/36_normal.jpeg", uri.toString())
        uri = UriUtils.fromAlternativeTags(jso, "unknown1", "unknown2")
        Assert.assertEquals("", uri.toString())
    }

    @Test
    fun testIsDownloadable() {
        for (uri in EMPTY_URIS) {
            Assert.assertFalse(UriUtils.isDownloadable(uri))
        }
        Assert.assertFalse(UriUtils.isDownloadable(UriUtils.fromString(".")))
        Assert.assertFalse(UriUtils.isDownloadable(UriUtils.fromString("something")))
        Assert.assertFalse(UriUtils.isDownloadable(UriUtils.fromString("something")))
        Assert.assertFalse("We cannot download ftp", UriUtils.isDownloadable(UriUtils.fromString("ftp://somedomain.example.com")))
        Assert.assertFalse(UriUtils.isDownloadable(UriUtils.fromString("content://somedomain.example.com")))
        Assert.assertTrue(UriUtils.isDownloadable(UriUtils.fromString("http://somedomain.example.com")))
        Assert.assertTrue(UriUtils.isDownloadable(UriUtils.fromString("https://somedomain.example.com")))
    }

    companion object {
        private val EMPTY_URIS: Array<Uri?> = arrayOf(null, Uri.EMPTY, Uri.parse(""),
                UriUtils.fromString(""), UriUtils.fromString(" "))

        fun assertEndpoint(endpointType: ActorEndpointType?, value: String?, actor: Actor) {
            Assert.assertEquals("Endpoint $endpointType of $actor",
                    UriUtils.toDownloadableOptional(value), actor.getEndpoint(endpointType))
        }
    }
}
