/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.test.InstrumentationTestCase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.origin.Origin;

import java.util.List;

public class MbUserTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testFromBodyText() {
        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME);
        String webFingerId3 = "anotherUser@somedomain.org";
        String body = "@" + TestSuite.GNUSOCIAL_TEST_ACCOUNT_USERNAME + " @" + TestSuite.GNUSOCIAL_TEST_ACCOUNT2_USERNAME
                + " Please take this into account\n@" + webFingerId3
                + " @" + TestSuite.GNUSOCIAL_TEST_ACCOUNT2_USERNAME;
        List<MbUser> users = MbUser.fromBodyText(origin, body, false);
        String msgLog = body + " -> " + users;
        assertEquals(msgLog, 3, users.size());
        assertEquals(msgLog, TestSuite.GNUSOCIAL_TEST_ACCOUNT_USERNAME, users.get(0).getUserName());
        assertEquals(msgLog, TestSuite.GNUSOCIAL_TEST_ACCOUNT2_USERNAME, users.get(1).getUserName());
        assertEquals(msgLog, webFingerId3, users.get(2).getWebFingerId());
    }

    public void testIsWebFingerIdValid() {
        checkWebFingerId("", false);
        checkWebFingerId("someUser.", false);
        checkWebFingerId("someUser ", false);
        checkWebFingerId("some.user", false);
        checkWebFingerId("some.user@example.com", true);
        checkWebFingerId("t131t@identi.ca/PumpIo", false);
        checkWebFingerId("some@example.com.", false);
        checkWebFingerId("some@user", true);
    }

    private void checkWebFingerId(String userName, boolean valid) {
        assertEquals("Username '" + userName + "' " + (valid ? "is valid" : "invalid"), valid,
                MbUser.isWebFingerIdValid(userName));
    }
}
