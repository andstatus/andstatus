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
package org.andstatus.app.net.http

import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.UriUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test

class HttpReadResultTest {
    @Test
    @Throws(JSONException::class)
    fun testResultToArray() {
        val uri = UriUtils.fromString("https://example.com/somepath/file.html")
        val `in` = ("{\"results\":[{\"text\":\"Text1\",\"to_user\":\"someuser\",\"from_user\":\"author1\"},"
                + "{\"text\":\"Text2\",\"to_user\":\"andstatus\",\"from_user\":\"otherauthor\"}]"
                + ",\"since_id\":\"Wed, 05 Mar 2014 16:37:17 +0100\""
                + "}")
        val request1: HttpRequest = HttpRequest.Companion.of(ApiRoutineEnum.HOME_TIMELINE, uri)
                .withPostParams(JSONObject())
        val result1 = request1.newResult()
        result1.strResponse = `in`
        val jsa = result1.getJsonArray("items").get()
        Assert.assertEquals(2, jsa.length().toLong())
        Assert.assertFalse(result1.request.postParams.isPresent)
        Assert.assertFalse(result1.toString(), result1.toString().contains("posted"))
        val request2: HttpRequest = HttpRequest.Companion.of(ApiRoutineEnum.HOME_TIMELINE, uri)
                .withPostParams(JSONObject("{}"))
        Assert.assertFalse(request2.postParams.isPresent)
        val request3: HttpRequest = HttpRequest.Companion.of(ApiRoutineEnum.HOME_TIMELINE, uri)
                .withPostParams(JSONObject("{\"text\":\"Text1\"}"))
        Assert.assertTrue(request3.toString(), request3.postParams.isPresent)
        MatcherAssert.assertThat(request3.toString(), CoreMatchers.containsString("POST:"))
    }
}
