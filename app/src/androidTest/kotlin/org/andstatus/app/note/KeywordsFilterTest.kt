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
package org.andstatus.app.note

import org.andstatus.app.note.KeywordsFilter.Keyword
import org.andstatus.app.util.MyHtml
import org.junit.Assert
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class KeywordsFilterTest {
    @Test
    fun testPhrases() {
        assertOneQueryToKeywords("<a/>")
        assertOneQueryToKeywords("contains:")
        assertOneQueryToKeywords("contains:<br/>")
        assertOneQueryToKeywords("contains:a", Keyword("a", true))
        var query = "\"deleted notice\""
        val keywordDN = Keyword(",deleted,notice,")
        assertOneQueryToKeywords(query, keywordDN)
        val body1 = "Looking for the deleted notice"
        assertMatchAll(query, body1)
        assertMatchAny(query, body1)
        assertNotMatchAll(query, body1 + "s")
        assertNotMatchAny(query, body1 + "s")
        query = "word $query"
        val keywordW = Keyword(",word,")
        assertOneQueryToKeywords(query, keywordW, keywordDN)
        assertNotMatchAll(query, body1)
        val body2 = "$body1 with a word, that is interesting. And with a link to http://andstatus.org/somepath"
        assertMatchAll(query, body2)
        assertMatchAny("those this that", body2)
        assertNotMatchAny("something other", body2)
        query = "  , Word, $query"
        assertOneQueryToKeywords(query, keywordW, keywordDN)
        assertNotMatchAll(query, body1)
        assertMatchAll(query, body2)
        query += ", " + KeywordsFilter.Companion.CONTAINS_PREFIX + "//andstatus.org/, "
        val keywordContainsDomainName = Keyword("//andstatus.org/", true)
        assertOneQueryToKeywords(query, keywordW, keywordDN, keywordContainsDomainName)
        assertNotMatchAll(query, body1)
        assertMatchAll(query, body2)
        query += ", Something"
        val keywordSomething = Keyword(",something,")
        assertOneQueryToKeywords(query, keywordW, keywordDN, keywordContainsDomainName, keywordSomething)
        assertNotMatchAll(query, body1)
        assertNotMatchAll(query, body2)
    }

    private fun assertOneQueryToKeywords(query: String?, vararg keywords: Keyword?) {
        val size = keywords.size
        val filter1 = KeywordsFilter(query)
        Assert.assertEquals(filter1.keywordsToFilter.toString(), size.toLong(), filter1.keywordsToFilter.size.toLong())
        for (ind in 0 until size) {
            Assert.assertEquals(filter1.keywordsToFilter.toString(), keywords[ind], filter1.keywordsToFilter[ind])
        }
    }

    private fun assertMatchAll(query: String?, body: String?) {
        val bodyToSearch = MyHtml.getContentToSearch(body)
        Assert.assertTrue("query '$query' doesn't match: '$body'", KeywordsFilter(query).matchedAll(bodyToSearch))
    }

    private fun assertNotMatchAll(query: String?, body: String?) {
        val bodyToSearch = MyHtml.getContentToSearch(body)
        Assert.assertFalse("query '$query' matched: '$body'", KeywordsFilter(query).matchedAll(bodyToSearch))
    }

    private fun assertMatchAny(query: String?, body: String?) {
        val bodyToSearch = MyHtml.getContentToSearch(body)
        Assert.assertTrue("no keywords from '$query' match: '$body'", KeywordsFilter(query).matchedAny(bodyToSearch))
    }

    private fun assertNotMatchAny(query: String?, body: String?) {
        val bodyToSearch = MyHtml.getContentToSearch(body)
        Assert.assertFalse("Some keyword from '$query' match: '$body'", KeywordsFilter(query).matchedAny(bodyToSearch))
    }
}
