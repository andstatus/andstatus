package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

public class MyProviderTest extends InstrumentationTestCase {
    public void testQuoteIfNotQuoted() {
        assertEquals("Empty string", String.valueOf('\'') + String.valueOf('\''), MyQuery.quoteIfNotQuoted(""));
        assertEquals("Null", String.valueOf('\'') + String.valueOf('\''), MyQuery.quoteIfNotQuoted(null));
        assertEquals("string", "'toQuote'", MyQuery.quoteIfNotQuoted("toQuote"));
        assertEquals("quoted", "'toQuote'", MyQuery.quoteIfNotQuoted("'toQuote'"));
        assertEquals("quoted", "'to''Quote'", MyQuery.quoteIfNotQuoted("'to'Quote'"));
        assertEquals("quoted", "'''toQuote'", MyQuery.quoteIfNotQuoted("'toQuote"));
        assertEquals("quoted", "'''toQuo''te'", MyQuery.quoteIfNotQuoted("'toQuo'te"));
    }
}
