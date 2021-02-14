package org.andstatus.app.net.social

import org.andstatus.app.context.TestSuite
import org.junit.Assert
import org.junit.Test
import java.util.*

class ConnectionTest {
    @Test
    fun testParseDate() {
        val connection: Connection = ConnectionEmpty.Companion.EMPTY
        val badStringDate = "Wrong Date Format"
        val unixDate = connection.parseDate(badStringDate)
        Assert.assertEquals("Bad date (" + badStringDate + ") " + Date(unixDate), 0, unixDate)
        parseOneDate(connection, "Fri Oct 24 13:34:38 -0700 2014",
                TestSuite.utcTime(2014, Calendar.OCTOBER, 24, 20, 34, 38))
        parseOneDate(connection, "Wed Nov 27 09:27:01 -0300 2013",
                TestSuite.utcTime(2013, Calendar.NOVEMBER, 27, 12, 27, 1))
        parseOneDate(connection, "Thu Sep 26 22:23:05 GMT+04:00 2013",
                TestSuite.utcTime(2013, Calendar.SEPTEMBER, 26, 18, 23, 5))
    }

    private fun parseOneDate(connection: Connection?, stringDate: String?, date: Date?) {
        val unixDate: Long
        unixDate = connection.parseDate(stringDate)
        Assert.assertEquals("Testing the date: " + date + " (string: " + stringDate + ") vs " + Date(unixDate).toString() + "; ", date.getTime(), unixDate)
    }
}