package org.andstatus.app.data

import org.junit.Assert
import org.junit.Test

class MyProviderTest {
    @Test
    fun testQuoteIfNotQuoted() {
        Assert.assertEquals("Empty string", '\''.toString() + '\''.toString(), MyQuery.quoteIfNotQuoted(""))
        Assert.assertEquals("Null", '\''.toString() + '\''.toString(), MyQuery.quoteIfNotQuoted(null))
        Assert.assertEquals("string", "'toQuote'", MyQuery.quoteIfNotQuoted("toQuote"))
        Assert.assertEquals("quoted", "'toQuote'", MyQuery.quoteIfNotQuoted("'toQuote'"))
        Assert.assertEquals("quoted", "'to''Quote'", MyQuery.quoteIfNotQuoted("'to'Quote'"))
        Assert.assertEquals("quoted", "'''toQuote'", MyQuery.quoteIfNotQuoted("'toQuote"))
        Assert.assertEquals("quoted", "'''toQuo''te'", MyQuery.quoteIfNotQuoted("'toQuo'te"))
    }
}
