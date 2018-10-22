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

package org.andstatus.app.appwidget;

import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;

import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.notification.NotificationEvents;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Runs various tests...
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetProviderTest {
    private MyContext myContext;
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
        myContext = MyContextHolder.get();
        MyServiceManager.setServiceUnavailable();
    }

    @Test
    public void testTimeFormatting() {
        MyLog.v(this, "testTimeFormatting started");
    	
        for (DateTest dateTest : getDateTests()) {
            if (dateTest == null) {
                break;
            }
            long startMillis = dateTest.date1.toMillis(false /* use isDst */);
            long endMillis = dateTest.date2.toMillis(false /* use isDst */);
            int flags = dateTest.flags;
            String output = DateUtils.formatDateRange(myContext.context(), startMillis, endMillis, flags);
            String output2 = MyRemoteViewData.formatWidgetTime(myContext.context(), startMillis, endMillis);
            MyLog.v(this, "\"" + output + "\"; \"" + output2 + "\"");
        }         
    }

    private static class DateTest {
        Time date1;
        Time date2;
        int flags;
        
        DateTest(long startMillis, long endMillis) {
        	date1 = new Time();
        	date1.set(startMillis);
        	date2 = new Time();
        	date2.set(endMillis);
        	flags = DateUtils.FORMAT_24HOUR | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME;
        }
    }
    
    private List<DateTest> getDateTests() {
        List<DateTest> dateTests = new ArrayList<>();
    	Calendar cal1 = Calendar.getInstance();
    	Calendar cal2 = Calendar.getInstance();

    	cal1.setTimeInMillis(System.currentTimeMillis());
    	cal2.setTimeInMillis(System.currentTimeMillis());
    	dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));
    	
    	cal1.roll(Calendar.SECOND, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal1.roll(Calendar.SECOND, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));
    	
    	cal1.roll(Calendar.MINUTE, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal1.add(Calendar.SECOND, 5);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal1.roll(Calendar.MINUTE, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal1.roll(Calendar.HOUR, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal1.roll(Calendar.HOUR, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal1.roll(Calendar.DAY_OF_YEAR, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));
    	
    	cal1.roll(Calendar.DAY_OF_YEAR, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal2.roll(Calendar.MINUTE, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal2.roll(Calendar.HOUR, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal2.roll(Calendar.HOUR, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));

    	cal1.roll(Calendar.YEAR, false);
        dateTests.add(new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis()));
        return dateTests;
    }

    @Test
    public void testReceiver() {
        final String method = "testReceiver";
    	MyLog.i(this, method + "; started");

        long dateSinceMin = System.currentTimeMillis();
    	// To set dateSince and dateChecked correctly!
        updateWidgets(AppWidgets.of(myContext), NotificationEventType.ANNOUNCE, 1);
        DbUtils.waitMs(method, 2000);
        long dateSinceMax = System.currentTimeMillis();
        DbUtils.waitMs(method, 2000);
        myContext.getNotifier().clearAll();

        AppWidgets appWidgets = AppWidgets.of(myContext);
        checkDateChecked(appWidgets, dateSinceMin, dateSinceMax);
        checkDateSince(appWidgets, dateSinceMin, dateSinceMax);
        checkEvents(0, 0, 0);

    	int numMentions = 3;
        updateWidgets(appWidgets, NotificationEventType.MENTION, numMentions);
    	
    	int numPrivate = 1;
        updateWidgets(appWidgets, NotificationEventType.PRIVATE, numPrivate);
    	
    	int numReblogs = 7;
        updateWidgets(appWidgets, NotificationEventType.ANNOUNCE, numReblogs);
    	
        checkEvents(numMentions, numPrivate, numReblogs);
        
        long dateCheckedMin = System.currentTimeMillis();  
        numMentions++;
        updateWidgets(appWidgets, NotificationEventType.MENTION, 1);
        checkEvents(numMentions, numPrivate, numReblogs);
        long dateCheckedMax = System.currentTimeMillis();
        
        checkDateSince(appWidgets, dateSinceMin, dateSinceMax);
        checkDateChecked(appWidgets, dateCheckedMin, dateCheckedMax);
    }

    private void checkEvents(long numMentions, long numPrivate, long numReblogs) {
        NotificationEvents events = myContext.getNotifier().getEvents();
        assertEquals("Mentions", numMentions, events.getCount(NotificationEventType.MENTION));
        assertEquals("Private", numPrivate, events.getCount(NotificationEventType.PRIVATE));
        assertEquals("Reblogs", numReblogs, events.getCount(NotificationEventType.ANNOUNCE));
    }

    private void checkDateSince(AppWidgets appWidgets, long dateMin, long dateMax) {
        final String method = "checkDateSince";
        DbUtils.waitMs(method, 500);

        if (appWidgets.isEmpty()) {
            MyLog.i(this, method + "; No appWidgets found");
        }
        for (MyAppWidgetData widgetData : appWidgets.list()) {
            assertDatePeriod(method, dateMin, dateMax, widgetData.dateSince);
        }
    }

    private void checkDateChecked(AppWidgets appWidgets, long dateMin, long dateMax) {
        final String method = "checkDateChecked";
        DbUtils.waitMs(method, 500);

        if (appWidgets.isEmpty()) {
            MyLog.i(this, method + "; No appWidgets found");
        }
        for (MyAppWidgetData widgetData : appWidgets.list()) {
            assertDatePeriod(method, dateMin, dateMax, widgetData.dateLastChecked);
        }
    }
    
    private void assertDatePeriod(String message, long dateMin, long dateMax, long dateActual) {
        if (dateActual >= dateMin && dateActual <= dateMax) {
            return;
        }
        if (dateActual == 0 ) {
            fail( message + " actual date is zero");
        } else if (dateActual < dateMin) {
            fail( message + " actual date " + dateTimeFormatted(dateActual) 
                    + " is less than min date " + dateTimeFormatted(dateMin)
                    + " min by " + (dateMin - dateActual) + " ms");
        } else {
            fail( message + " actual date " + dateTimeFormatted(dateActual) 
                    + " is larger than max date " + dateTimeFormatted(dateMax)
                    + " max by " + (dateActual - dateMax) + " ms");
        }
    }
    
    private static String dateTimeFormatted(long date) {
        return DateFormat.format("yyyy-MM-dd HH:mm:ss", new Date(date)).toString();
    }

	/** 
	 * Update AndStatus Widget(s),
	 * if there are any installed... (e.g. on the Home screen...)
	 * @see MyAppWidgetProvider
	 */
	private void updateWidgets(AppWidgets appWidgets, NotificationEventType eventType, int increment) {
        final String method = "updateWidgets";
        DbUtils.waitMs(method, 500);
        for (int ind = 0; ind < increment; ind++ ) {
            NotificationEvents.loadEvent(appWidgets.events.map, appWidgets.events.enabledEvents, eventType,
                    DemoData.demoData.getConversationMyAccount(), System.currentTimeMillis());
        }
        appWidgets.updateData().updateViews();
	}
	
}