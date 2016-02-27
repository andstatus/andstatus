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
import android.test.InstrumentationTestCase;

import org.json.JSONException;
import org.json.JSONObject;

public class UriUtilsTest extends InstrumentationTestCase {
    public void testIsEmpty() {
        assertTrue(UriUtils.isEmpty(null));
        assertTrue(UriUtils.isEmpty(Uri.EMPTY));
        assertTrue(UriUtils.isEmpty(Uri.parse("")));
        assertTrue(UriUtils.isEmpty(UriUtils.fromString("")));
        assertTrue(UriUtils.isEmpty(UriUtils.fromString(" ")));
        assertFalse(UriUtils.isEmpty(UriUtils.fromString(".")));
    }

    public void testFromString() {
        assertTrue(UriUtils.fromString(null) != null);
        assertTrue(UriUtils.fromString("") != null);
        assertTrue(UriUtils.fromString("something") != null);
    }

    public void testNotNull() {
        assertEquals(UriUtils.notNull(null), Uri.EMPTY);
        Uri uri = Uri.parse("http://some.org/");
        assertEquals(UriUtils.notNull(uri), uri);
    }

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

}
