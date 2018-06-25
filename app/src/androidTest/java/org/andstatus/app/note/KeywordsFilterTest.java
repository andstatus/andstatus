/*
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

package org.andstatus.app.note;

import org.andstatus.app.note.KeywordsFilter.Keyword;
import org.andstatus.app.util.MyHtml;
import org.junit.Test;

import static org.andstatus.app.note.KeywordsFilter.CONTAINS_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author yvolk@yurivolkov.com
 */
public class KeywordsFilterTest {

    @Test
    public void testPhrases() {

        assertOneQueryToKeywords("<a/>");
        assertOneQueryToKeywords("contains:");
        assertOneQueryToKeywords("contains:<br/>");
        assertOneQueryToKeywords("contains:a", new Keyword("a", true));

        String query = "\"deleted notice\"";
        final Keyword keywordDN = new Keyword(",deleted,notice,");
        assertOneQueryToKeywords(query, keywordDN);
        final String body1 = "Looking for the deleted notice";
        assertMatchAll(query, body1);
        assertMatchAny(query, body1);
        assertNotMatchAll(query, body1 + "s");
        assertNotMatchAny(query, body1 + "s");

        query = "word " + query;
        final Keyword keywordW = new Keyword(",word,");
        assertOneQueryToKeywords(query, keywordW, keywordDN);
        assertNotMatchAll(query, body1);
        final String body2 = body1 + " with a word, that is interesting. And with a link to http://andstatus.org/somepath";
        assertMatchAll(query, body2);
        assertMatchAny("those this that", body2);
        assertNotMatchAny("something other", body2);

        query = "  , Word, " + query;
        assertOneQueryToKeywords(query, keywordW, keywordDN);
        assertNotMatchAll(query, body1);
        assertMatchAll(query, body2);

        query += ", " + CONTAINS_PREFIX + "//andstatus.org/, ";
        final Keyword keywordContainsDomainName = new Keyword("//andstatus.org/", true);
        assertOneQueryToKeywords(query, keywordW, keywordDN, keywordContainsDomainName);
        assertNotMatchAll(query, body1);
        assertMatchAll(query, body2);

        query += ", Something";
        final Keyword keywordSomething = new Keyword(",something,");
        assertOneQueryToKeywords(query, keywordW, keywordDN, keywordContainsDomainName, keywordSomething);
        assertNotMatchAll(query, body1);
        assertNotMatchAll(query, body2);
    }

    private void assertOneQueryToKeywords(String query, Keyword... keywords) {
        int size = keywords.length;
        KeywordsFilter filter1 = new KeywordsFilter(query);
        assertEquals(filter1.keywordsToFilter.toString(), size, filter1.keywordsToFilter.size());
        for ( int ind = 0; ind < size; ind++) {
            assertEquals(filter1.keywordsToFilter.toString(), keywords[ind], filter1.keywordsToFilter.get(ind));
        }
    }

    private void assertMatchAll(String query, String body) {
        final String bodyToSearch = MyHtml.getContentToSearch(body);
        assertTrue("query '" + query + "' doesn't match: '" + body + "'", new KeywordsFilter(query).matchedAll(bodyToSearch));
    }

    private void assertNotMatchAll(String query, String body) {
        final String bodyToSearch = MyHtml.getContentToSearch(body);
        assertFalse("query '" + query + "' matched: '" + body + "'", new KeywordsFilter(query).matchedAll(bodyToSearch));
    }

    private void assertMatchAny(String query, String body) {
        final String bodyToSearch = MyHtml.getContentToSearch(body);
        assertTrue("no keywords from '" + query + "' match: '" + body + "'", new KeywordsFilter(query).matchedAny(bodyToSearch));
    }

    private void assertNotMatchAny(String query, String body) {
        final String bodyToSearch = MyHtml.getContentToSearch(body);
        assertFalse("Some keyword from '" + query + "' match: '" + body + "'", new KeywordsFilter(query).matchedAny(bodyToSearch));
    }
}
