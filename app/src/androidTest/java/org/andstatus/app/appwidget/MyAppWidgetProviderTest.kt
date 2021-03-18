/*
 * Copyright (C) 2010-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.appwidget

import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Time
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.notification.NotifierTest
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.properties.Delegates

/**
 * Runs various tests...
 * @author yvolk@yurivolkov.com
 */
class MyAppWidgetProviderTest {
    private var myContext: MyContext by Delegates.notNull<MyContext>()
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        myContext =  MyContextHolder.myContextHolder.getNow()
        MyServiceManager.Companion.setServiceUnavailable()
    }

    @Test
    fun testTimeFormatting() {
        MyLog.v(this, "testTimeFormatting started")
        for (dateTest in getDateTests()) {
            val startMillis = dateTest.date1.toMillis(false /* use isDst */)
            val endMillis = dateTest.date2.toMillis(false /* use isDst */)
            val flags = dateTest.flags
            val output = DateUtils.formatDateRange(myContext.context(), startMillis, endMillis, flags)
            val output2: String = MyRemoteViewData.Companion.formatWidgetTime(myContext.context(), startMillis, endMillis)
            MyLog.v(this, "\"$output\"; \"$output2\"")
        }
    }

    private class DateTest(startMillis: Long, endMillis: Long) {
        var date1: Time = Time()
        var date2: Time = Time()
        var flags: Int

        init {
            date1.set(startMillis)
            date2.set(endMillis)
            flags = DateUtils.FORMAT_24HOUR or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
        }
    }

    private fun getDateTests(): MutableList<DateTest> {
        val dateTests: MutableList<DateTest> = ArrayList()
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance()
        cal1.timeInMillis = System.currentTimeMillis()
        cal2.timeInMillis = System.currentTimeMillis()
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.SECOND, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.SECOND, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.MINUTE, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.add(Calendar.SECOND, 5)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.MINUTE, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.HOUR, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.HOUR, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.DAY_OF_YEAR, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.DAY_OF_YEAR, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal2.roll(Calendar.MINUTE, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal2.roll(Calendar.HOUR, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal2.roll(Calendar.HOUR, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        cal1.roll(Calendar.YEAR, false)
        dateTests.add(DateTest(cal1.timeInMillis, cal2.timeInMillis))
        return dateTests
    }

    @Test
    fun testReceiver() {
        val method = "testReceiver"
        MyLog.i(this, "$method; started")
        myContext.getNotifier().clearAll()
        val dateSinceMin = System.currentTimeMillis()
        // To set dateSince and dateChecked correctly!
        updateWidgets(AppWidgets.Companion.of(myContext), NotificationEventType.ANNOUNCE, 1)
        DbUtils.waitMs(method, 2000)
        val dateSinceMax = System.currentTimeMillis()
        DbUtils.waitMs(method, 2000)
        myContext.getNotifier().clearAll()
        val appWidgets: AppWidgets = AppWidgets.Companion.of(myContext)
        checkDateChecked(appWidgets, dateSinceMin, dateSinceMax)
        checkDateSince(appWidgets, dateSinceMin, dateSinceMax)
        checkEvents(0, 0, 0)
        var numMentions = 3
        updateWidgets(appWidgets, NotificationEventType.MENTION, numMentions)
        val numPrivate = 1
        updateWidgets(appWidgets, NotificationEventType.PRIVATE, numPrivate)
        val numReblogs = 2
        updateWidgets(appWidgets, NotificationEventType.ANNOUNCE, numReblogs)
        checkEvents(numMentions.toLong(), numPrivate.toLong(), numReblogs.toLong())
        val dateCheckedMin = System.currentTimeMillis()
        numMentions++
        updateWidgets(appWidgets, NotificationEventType.MENTION, 1)
        checkEvents(numMentions.toLong(), numPrivate.toLong(), numReblogs.toLong())
        val dateCheckedMax = System.currentTimeMillis()
        checkDateSince(appWidgets, dateSinceMin, dateSinceMax)
        checkDateChecked(appWidgets, dateCheckedMin, dateCheckedMax)
    }

    private fun checkEvents(numMentions: Long, numPrivate: Long, numReblogs: Long) {
        val events = myContext.getNotifier().getEvents()
        Assert.assertEquals("Mentions", numMentions, events.getCount(NotificationEventType.MENTION))
        Assert.assertEquals("Private", numPrivate, events.getCount(NotificationEventType.PRIVATE))
        Assert.assertEquals("Reblogs", numReblogs, events.getCount(NotificationEventType.ANNOUNCE))
    }

    private fun checkDateSince(appWidgets: AppWidgets, dateMin: Long, dateMax: Long) {
        val method = "checkDateSince"
        DbUtils.waitMs(method, 500)
        if (appWidgets.isEmpty()) {
            MyLog.i(this, "$method; No appWidgets found")
        }
        for (widgetData in appWidgets.list()) {
            assertDatePeriod(method, dateMin, dateMax, widgetData.dateSince)
        }
    }

    private fun checkDateChecked(appWidgets: AppWidgets, dateMin: Long, dateMax: Long) {
        val method = "checkDateChecked"
        DbUtils.waitMs(method, 500)
        if (appWidgets.isEmpty()) {
            MyLog.i(this, "$method; No appWidgets found")
        }
        for (widgetData in appWidgets.list()) {
            assertDatePeriod(method, dateMin, dateMax, widgetData.dateLastChecked)
        }
    }

    private fun assertDatePeriod(message: String?, dateMin: Long, dateMax: Long, dateActual: Long) {
        if (dateActual >= dateMin && dateActual <= dateMax) {
            return
        }
        if (dateActual == 0L) {
            Assert.fail("$message actual date is zero")
        } else if (dateActual < dateMin) {
            Assert.fail(message + " actual date " + dateTimeFormatted(dateActual)
                    + " is less than min date " + dateTimeFormatted(dateMin)
                    + " min by " + (dateMin - dateActual) + " ms")
        } else {
            Assert.fail(message + " actual date " + dateTimeFormatted(dateActual)
                    + " is larger than max date " + dateTimeFormatted(dateMax)
                    + " max by " + (dateActual - dateMax) + " ms")
        }
    }

    /**
     * Update AndStatus Widget(s),
     * if there are any installed... (e.g. on the Home screen...)
     * @see MyAppWidgetProvider
     */
    private fun updateWidgets(appWidgets: AppWidgets, eventType: NotificationEventType, increment: Int) {
        val method = "updateWidgets"
        DbUtils.waitMs(method, 500)
        for (ind in 0 until increment) {
            NotifierTest.addNotificationEvent(appWidgets.events.myContext, eventType)
        }
        appWidgets.updateData().updateViews()
    }

    companion object {
        private fun dateTimeFormatted(date: Long): String {
            return DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(date)).toString()
        }
    }
}