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
        final String url = "https://example.com/somepath/file.html";
        final String in = "{\"results\":[{\"text\":\"Text1\",\"to_user\":\"someuser\",\"from_user\":\"author1\"}," 
                + "{\"text\":\"Text2\",\"to_user\":\"andstatus\",\"from_user\":\"otherauthor\"}]"
                + ",\"since_id\":\"Wed, 05 Mar 2014 16:37:17 +0100\""
                + "}";
        HttpReadResult result = new HttpReadResult(url);
        result.strResponse = in;
        JSONArray jsa =  result.getJsonArray();
        assertEquals(2, jsa.length());
        
        assertEquals(false, result.hasFormParams());
        result.setFormParams(new JSONObject());
        assertEquals(false, result.hasFormParams());
        result.setFormParams(new JSONObject("{}"));
        assertEquals(false, result.hasFormParams());
        assertFalse(result.toString(), result.toString().contains("posted"));
        result.setFormParams(new JSONObject("{\"text\":\"Text1\"}"));
        assertEquals(true, result.hasFormParams());
        assertTrue(result.toString(), result.toString().contains("posted"));
    }
}
