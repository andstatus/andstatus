package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

public class MyProviderTest extends InstrumentationTestCase {
    public void testQuoteIfNotQuoted() {
        assertEquals("Empty string", String.valueOf('\'') + String.valueOf('\''), MyProvider.quoteIfNotQuoted(""));
        assertEquals("Null", String.valueOf('\'') + String.valueOf('\''), MyProvider.quoteIfNotQuoted(null));
        assertEquals("string", "'toQuote'", MyProvider.quoteIfNotQuoted("toQuote"));
        assertEquals("quoted", "'toQuote'", MyProvider.quoteIfNotQuoted("'toQuote'"));
        assertEquals("quoted", "'to''Quote'", MyProvider.quoteIfNotQuoted("'to'Quote'"));
        assertEquals("quoted", "'''toQuote'", MyProvider.quoteIfNotQuoted("'toQuote"));
        assertEquals("quoted", "'''toQuo''te'", MyProvider.quoteIfNotQuoted("'toQuo'te"));
    }
}
