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
import android.text.format.Time;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

import android.text.format.DateUtils;
import android.test.InstrumentationTestCase;

import java.util.Calendar;
import java.util.Date;

/**
 * Runs various tests...
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetProviderTest extends InstrumentationTestCase {
    MyContext myContext;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
        myContext = MyContextHolder.get();
        MyServiceManager.setServiceUnavailable();
        initializeDateTests();
    }
    
    public void testTimeFormatting() throws Exception {
        MyLog.v(this, "testTimeFormatting started");
    	
        int len = dateTests.length;
        for (int index = 0; index < len; index++) {
            DateTest dateTest = dateTests[index];
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

    DateTest[] dateTests = new DateTest[101];
    
    static private class DateTest {
        public Time date1;
        public Time date2;
        public int flags;
        
        public DateTest(long startMillis, long endMillis) {
        	date1 = new Time();
        	date1.set(startMillis);
        	date2 = new Time();
        	date2.set(endMillis);
        	flags = DateUtils.FORMAT_24HOUR | DateUtils.FORMAT_SHOW_DATE 
        	| DateUtils.FORMAT_SHOW_TIME;
        }
    }
    
    private void initializeDateTests() {
    	// Initialize dateTests
    	int ind = 0;
    	Calendar cal1 = Calendar.getInstance();
    	Calendar cal2 = Calendar.getInstance();

    	cal1.setTimeInMillis(System.currentTimeMillis());
    	cal2.setTimeInMillis(System.currentTimeMillis());
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());
    	
    	ind += 1;
    	cal1.roll(Calendar.SECOND, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal1.roll(Calendar.SECOND, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());
    	
    	ind += 1;
    	cal1.roll(Calendar.MINUTE, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal1.add(Calendar.SECOND, 5);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal1.roll(Calendar.MINUTE, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal1.roll(Calendar.HOUR, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal1.roll(Calendar.HOUR, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());
    	
    	ind += 1;
    	cal1.roll(Calendar.DAY_OF_YEAR, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());
    	
    	ind += 1;
    	cal1.roll(Calendar.DAY_OF_YEAR, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal2.roll(Calendar.MINUTE, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal2.roll(Calendar.HOUR, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal2.roll(Calendar.HOUR, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());

    	ind += 1;
    	cal1.roll(Calendar.YEAR, false);
    	dateTests[ind] = new DateTest(cal1.getTimeInMillis(), cal2.getTimeInMillis());
    }

    public void testReceiver() throws Exception {
        final String method = "testReceiver";
    	MyLog.i(this, method + "; started");

        long dateSinceMin = System.currentTimeMillis();  
    	// To set dateSince correctly!
        updateWidgets(1, TimelineTypeEnum.HOME, 0);
        Thread.sleep(5000);
        long dateSinceMax = System.currentTimeMillis();  
        Thread.sleep(5000);
        AppWidgets.clearAndUpdateWidgets(myContext);
        checkWidgetData(0, 0, 0);
        checkDateChecked(dateSinceMin, dateSinceMax);
    	
    	int numMentions = 3;
        updateWidgets(0, TimelineTypeEnum.HOME, numMentions);
    	
    	int numDirect = 1;
        updateWidgets(numDirect, TimelineTypeEnum.DIRECT, 0);
    	
    	int numHome = 7;
        updateWidgets(numHome, TimelineTypeEnum.HOME, 0);
    	
        checkWidgetData(numMentions, numDirect, numHome);
        
        long dateCheckedMin = System.currentTimeMillis();  
        numMentions++;
        updateWidgets(0, TimelineTypeEnum.HOME, 1);
        checkWidgetData(numMentions, numDirect, numHome);
        long dateCheckedMax = System.currentTimeMillis();
        
        checkDateSince(dateSinceMin, dateSinceMax);
        checkDateChecked(dateCheckedMin, dateCheckedMax);
    }

    private void checkWidgetData(int numMentions, int numDirect, int numHome)
            throws InterruptedException {
        final String method = "checkWidgetData";
    	Thread.sleep(500);
    	
    	AppWidgets appWidgets = AppWidgets.newInstance(myContext);
    	if (appWidgets.isEmpty()) {
            MyLog.i(this, method + "; No appWidgets found");
    	}
    	for (MyAppWidgetData widgetData : appWidgets.collection()) {
            assertEquals("Mentions " + widgetData.toString(), numMentions, widgetData.numMentions);
    	    assertEquals("Direct " + widgetData.toString(), numDirect, widgetData.numDirectMessages);
            assertEquals("Home " + widgetData.toString(), numHome, widgetData.numHomeTimeline);
    	}
    }

    private void checkDateSince(long dateMin, long dateMax)
            throws InterruptedException {
        final String method = "checkDateSince";
        Thread.sleep(500);
        
        AppWidgets appWidgets = AppWidgets.newInstance(myContext);
        if (appWidgets.isEmpty()) {
            MyLog.i(this, method + "; No appWidgets found");
        }
        for (MyAppWidgetData widgetData : appWidgets.collection()) {
            assertDatePeriod(method, dateMin, dateMax, widgetData.dateSince);
        }
    }

    private void checkDateChecked(long dateMin, long dateMax)
            throws InterruptedException {
        final String method = "checkDateChecked";
        Thread.sleep(500);

        AppWidgets appWidgets = AppWidgets.newInstance(myContext);
        if (appWidgets.isEmpty()) {
            MyLog.i(this, method + "; No appWidgets found");
        }
        for (MyAppWidgetData widgetData : appWidgets.collection()) {
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
                    + " is less than expected " + dateTimeFormatted(dateMin) 
                    + " min by " + (dateMin - dateActual) + " ms");
        } else {
            fail( message + " actual date " + dateTimeFormatted(dateActual) 
                    + " is larger than expected " + dateTimeFormatted(dateMax) 
                    + " max by " + (dateActual - dateMax) + " ms");
        }
    }
    
    static String dateTimeFormatted(long date) {
        return DateFormat.format("yyyy-MM-dd HH:mm:ss", new Date(date)).toString();
    }

	/** 
	 * Update AndStatus Widget(s),
	 * if there are some installed... (e.g. on the Home screen...) 
	 * @throws InterruptedException 
	 * @see MyAppWidgetProvider
	 */
	private void updateWidgets(int msgAdded, TimelineTypeEnum timelineType, int mentionsAdded) throws InterruptedException{
        Thread.sleep(500);
        AppWidgets appWidgets = AppWidgets.newInstance(myContext);
        CommandResult result = new CommandResult();
        for (int count = 0; count < msgAdded; count++) {
            result.incrementMessagesCount(timelineType);
        }
        for (int count = 0; count < mentionsAdded; count++) {
            result.incrementMentionsCount();
        }
        appWidgets.updateData(result);
        appWidgets.updateViews();
	}
	
	
}