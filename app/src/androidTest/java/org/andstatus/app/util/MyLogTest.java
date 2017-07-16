/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.util;

import android.text.TextUtils;

import org.andstatus.app.context.TestSuite;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MyLogTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
    }

    @Test
    public void testObjTag() {
       Object tag = this;
       assertEquals("MyLogTest", MyLog.objToTag(tag));
       tag = this.getClass();
       assertEquals("MyLogTest", MyLog.objToTag(tag));
       tag = "Other tag";
       assertEquals(tag.toString(), MyLog.objToTag(tag));
       tag = null;
       assertEquals("(null)", MyLog.objToTag(tag));
    }

    @Test
    public void testLogFilename() {
        final String method = "testLogFilename";
        boolean isLogEnabled = MyLog.isLogToFileEnabled();

        MyLog.setLogToFile(true);
        assertFalse(TextUtils.isEmpty(MyLog.getLogFilename()));
        MyLog.v(this, method);
        File file = MyLog.getFileInLogDir(MyLog.getLogFilename(), true);
        assertTrue(file.exists());
        
        MyLog.setLogToFile(false);
        assertTrue(TextUtils.isEmpty(MyLog.getLogFilename()));
        assertTrue(file.delete());
        MyLog.v(this, method);
        assertEquals(MyLog.getLogFilename(), null);
        assertFalse(file.exists());

        if (isLogEnabled) {
            MyLog.setLogToFile(true);
        }
    }

    @Test
    public void testUniqueDateTimeFormatted() {
        String string1 = "";
        String string2 = "";
        for (int ind = 0; ind < 20; ind++) {
            long time1 = MyLog.uniqueCurrentTimeMS();
            string1 = MyLog.uniqueDateTimeFormatted();
            long time2 = MyLog.uniqueCurrentTimeMS();
            string2 = MyLog.uniqueDateTimeFormatted();
            assertTrue("Time:" + time1, time2 > time1);
            assertFalse(string1, string1.contains("SSS"));
            assertFalse(string1, string1.contains("HH"));
            assertTrue(string1, string1.contains("-"));
            assertFalse(string1, string1.equals(string2));
        }
        MyLog.v("testUniqueDateTimeFormatted", string1 + " " + string2);
    }
}
