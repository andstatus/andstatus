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

import android.net.Uri;

import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UriUtilsTest {
    private static Uri[] EMPTY_URIS = { null, Uri.EMPTY, Uri.parse(""),
            UriUtils.fromString(""), UriUtils.fromString(" ")};

    public static void assertEndpoint(ActorEndpointType endpointType, String value, Actor actor) {
        assertEquals("Endpoint " + endpointType + " of " + actor,
                UriUtils.toDownloadableOptional(value), actor.getEndpoint(endpointType));
    }

    @Test
    public void testIsEmpty() {
        for (Uri uri : EMPTY_URIS) {
            assertTrue(UriUtils.isEmpty(uri));
        }
        assertFalse(UriUtils.isEmpty(UriUtils.fromString(".")));
    }

    @Test
    public void testFromString() {
        assertTrue(UriUtils.fromString(null) != null);
        assertTrue(UriUtils.fromString("") != null);
        assertTrue(UriUtils.fromString("something") != null);
    }

    @Test
    public void testNotNull() {
        assertEquals(Uri.EMPTY, UriUtils.notNull(null));
        Uri uri = Uri.parse("http://some.org/");
        assertEquals(UriUtils.notNull(uri), uri);
    }

    @Test
    public void testFromJson() throws JSONException {
        JSONObject jso = new JSONObject(
            "{\"profile_image_url\":\"http://a0.twimg.com/profile_images/36_normal.jpeg\",\n"
            + "\"profile_image_url_https\":\"https://si0.twimg.com/profile_images/37_normal.jpeg\"}");
        Uri uri = UriUtils.fromAlternativeTags(jso, "profile_image_url_https", "profile_image_url");
        assertEquals("https://si0.twimg.com/profile_images/37_normal.jpeg", uri.toString());

        uri = UriUtils.fromAlternativeTags(jso, "profile_image_url", "profile_image_url_https");
        assertEquals("http://a0.twimg.com/profile_images/36_normal.jpeg", uri.toString());

        uri = UriUtils.fromAlternativeTags(jso, "unknown1", "unknown2");
        assertEquals("", uri.toString());
    }

    @Test
    public void testIsDownloadable() {
        for (Uri uri : EMPTY_URIS) {
            assertFalse(UriUtils.isDownloadable(uri));
        }
        assertFalse(UriUtils.isDownloadable(UriUtils.fromString(".")));
        assertFalse(UriUtils.isDownloadable(UriUtils.fromString("something")));
        assertFalse(UriUtils.isDownloadable(UriUtils.fromString("something")));
        assertFalse("We cannot download ftp", UriUtils.isDownloadable(UriUtils.fromString("ftp://somedomain.example.com")));
        assertFalse(UriUtils.isDownloadable(UriUtils.fromString("content://somedomain.example.com")));
        assertTrue(UriUtils.isDownloadable(UriUtils.fromString("http://somedomain.example.com")));
        assertTrue(UriUtils.isDownloadable(UriUtils.fromString("https://somedomain.example.com")));
    }

}
