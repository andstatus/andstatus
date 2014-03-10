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

package org.andstatus.app.net;

import android.test.InstrumentationTestCase;

import org.json.JSONArray;
import org.json.JSONTokener;

public class HttpApacheUtilsTest extends InstrumentationTestCase {
    
    public void testJsonTokenerToArray() throws ConnectionException {
        final String in = "{\"results\":[{\"text\":\"Text1\",\"to_user\":\"someuser\",\"from_user\":\"author1\"}," 
                + "{\"text\":\"Text2\",\"to_user\":\"andstatus\",\"from_user\":\"otherauthor\"}]"
                + ",\"since_id\":\"Wed, 05 Mar 2014 16:37:17 +0100\""
                + "}";
        HttpApacheUtils utils = new HttpApacheUtils(null);
        JSONTokener jst = new JSONTokener(in);
        JSONArray jsa = utils.jsonTokenerToArray(jst);
        assertEquals(2, jsa.length());
    }
}
