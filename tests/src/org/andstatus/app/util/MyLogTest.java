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

import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.context.TestSuite;

import java.io.File;

public class MyLogTest  extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }

    public void testObjToString() {
       Object tag = this;
       assertEquals("MyLogTest", MyLog.objTagToString(tag));
       tag = this.getClass();
       assertEquals("MyLogTest", MyLog.objTagToString(tag));
       tag = "Other tag";
       assertEquals(tag.toString(), MyLog.objTagToString(tag));
       tag = null;
       assertEquals("(null)", MyLog.objTagToString(tag));
    }
    
    public void testLogFilename() {
        final String method = "testLogFilename";
        MyLog.setLogToFile(true);
        assertFalse(TextUtils.isEmpty(MyLog.getLogFilename()));
        File file = MyLog.getLogFile(MyLog.getLogFilename(), true);
        MyLog.v(this, method);
        assertTrue(file.exists());
        
        MyLog.setLogToFile(false);
        assertTrue(TextUtils.isEmpty(MyLog.getLogFilename()));
        file.delete();
        MyLog.v(this, method);
        assertFalse(file.exists());
    }
}
