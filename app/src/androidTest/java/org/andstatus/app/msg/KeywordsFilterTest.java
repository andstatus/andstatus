/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.Travis;

/**
 * @author yvolk@yurivolkov.com
 */
@Travis
public class KeywordsFilterTest extends InstrumentationTestCase {
    public void testPhrases() {
        String string1 = "\"deleted notice\"";
        final String keywordDN = ",deleted,notice,";
        assertOne(string1, keywordDN);

        string1 = "word " + string1;
        final String keywordW = ",word,";
        assertOne(string1, keywordW, keywordDN);

        string1 = "  , Word, " + string1;
        assertOne(string1, keywordW, keywordDN);

        string1 += ", Something";
        assertOne(string1, keywordW, keywordDN, ",something,");
    }

    private void assertOne(String string1, String... values) {
        int size = values.length;
        KeywordsFilter filter1 = new KeywordsFilter(string1);
        assertEquals(filter1.keywordsToFilter.toString(), size, filter1.keywordsToFilter.size());
        for ( int ind = 0; ind < size; ind++) {
            assertEquals(filter1.keywordsToFilter.toString(), values[ind], filter1.keywordsToFilter.get(ind));
        }
    }
}
