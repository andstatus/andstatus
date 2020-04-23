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

package org.andstatus.app.net.http;

import android.net.Uri;

import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpReadResultTest {

    @Test
    public void testResultToArray() throws ConnectionException, JSONException {
        final Uri uri = UriUtils.fromString("https://example.com/somepath/file.html");
        final String in = "{\"results\":[{\"text\":\"Text1\",\"to_user\":\"someuser\",\"from_user\":\"author1\"}," 
                + "{\"text\":\"Text2\",\"to_user\":\"andstatus\",\"from_user\":\"otherauthor\"}]"
                + ",\"since_id\":\"Wed, 05 Mar 2014 16:37:17 +0100\""
                + "}";
        HttpReadResult result1 = new HttpReadResult(uri, new JSONObject());
        result1.strResponse = in;
        JSONArray jsa =  result1.getJsonArray("items").get();
        assertEquals(2, jsa.length());
        assertEquals(false, result1.request.formParams.isPresent());
        assertFalse(result1.toString(), result1.toString().contains("posted"));

        HttpReadResult result2 = new HttpReadResult(uri, new JSONObject("{}"));
        assertEquals(false, result2.request.formParams.isPresent());

        HttpReadResult result3 = new HttpReadResult(uri, new JSONObject("{\"text\":\"Text1\"}"));
        assertEquals(true, result3.request.formParams.isPresent());
        assertTrue(result3.toString(), result3.toString().contains("posted"));
    }
}
