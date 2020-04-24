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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpReadResultTest {

    @Test
    public void testResultToArray() throws JSONException {
        final Uri uri = UriUtils.fromString("https://example.com/somepath/file.html");
        final String in = "{\"results\":[{\"text\":\"Text1\",\"to_user\":\"someuser\",\"from_user\":\"author1\"}," 
                + "{\"text\":\"Text2\",\"to_user\":\"andstatus\",\"from_user\":\"otherauthor\"}]"
                + ",\"since_id\":\"Wed, 05 Mar 2014 16:37:17 +0100\""
                + "}";
        HttpRequest request1 = HttpRequest.of(MyContextHolder.get(), Connection.ApiRoutineEnum.HOME_TIMELINE, uri)
                .withPostParams(new JSONObject());
        HttpReadResult result1 = request1.newResult();
        result1.strResponse = in;
        JSONArray jsa =  result1.getJsonArray("items").get();
        assertEquals(2, jsa.length());
        assertFalse(result1.request.postParams.isPresent());
        assertFalse(result1.toString(), result1.toString().contains("posted"));

        HttpRequest request2 = HttpRequest.of(MyContextHolder.get(), Connection.ApiRoutineEnum.HOME_TIMELINE, uri)
                .withPostParams(new JSONObject("{}"));
        assertFalse(request2.postParams.isPresent());

        HttpRequest request3 = HttpRequest.of(MyContextHolder.get(), Connection.ApiRoutineEnum.HOME_TIMELINE, uri)
                .withPostParams(new JSONObject("{\"text\":\"Text1\"}"));
        assertTrue(request3.toString(), request3.postParams.isPresent());
        assertThat(request3.toString(), containsString("POST:"));
    }
}
