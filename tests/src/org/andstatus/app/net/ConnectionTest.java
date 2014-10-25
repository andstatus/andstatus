package org.andstatus.app.net;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.TestSuite;

import java.util.Calendar;
import java.util.Date;

public class ConnectionTest extends InstrumentationTestCase {
    
    public void testParseDate() {
        Connection connection = new ConnectionEmpty();
        
        String badStringDate = "Wrong Date Format";
        long unixDate = connection.parseDate(badStringDate);
        assertEquals("Bad date (" + badStringDate + ") " + new Date(unixDate), 0, unixDate );

        parseOneDate(connection, "Fri Oct 24 13:34:38 -0700 2014", 
                TestSuite.utcTime(2014, Calendar.OCTOBER, 24, 20, 34, 38));
        
        parseOneDate(connection, "Wed Nov 27 09:27:01 -0300 2013", 
                TestSuite.utcTime(2013, Calendar.NOVEMBER, 27, 12, 27, 01));

        parseOneDate(connection, "Thu Sep 26 22:23:05 GMT+04:00 2013", 
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 26, 18, 23, 05));
    }

    private void parseOneDate(Connection connection, String stringDate, Date date) {
        long unixDate;
        unixDate = connection.parseDate(stringDate);
        assertEquals("Testing the date: " + date + " (string: " + stringDate + ") vs " + new Date(unixDate).toString() + "; ", date.getTime(), unixDate);
    }
}
