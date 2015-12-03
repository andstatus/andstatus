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

    public void testFromBodyText1() {
        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME);
        String webFingerId2 = "anotherUser@somedomain.org";
        String shortUsername3 = "shortusername";
        String body = "@" + TestSuite.GNUSOCIAL_TEST_ACCOUNT_USERNAME + " @" + TestSuite.GNUSOCIAL_TEST_ACCOUNT2_USERNAME
                + " Please take this into account\n@" + webFingerId2
                + " @" + TestSuite.GNUSOCIAL_TEST_ACCOUNT2_USERNAME
                + " And let me mention: @" + shortUsername3;
        List<MbUser> users = MbUser.fromOriginAndUserOid(origin.getId(), "").fromBodyText(body, false);
        String msgLog = body + " -> " + users;
        assertEquals(msgLog, 4, users.size());
        assertEquals(msgLog, TestSuite.GNUSOCIAL_TEST_ACCOUNT_USERNAME, users.get(0).getUserName());
        assertEquals(msgLog, TestSuite.GNUSOCIAL_TEST_ACCOUNT2_USERNAME, users.get(1).getUserName());
        assertEquals(msgLog, webFingerId2, users.get(2).getWebFingerId());
        assertEquals(msgLog, shortUsername3, users.get(3).getUserName());
    }

    public void testFromBodyText2() {
        final String USERNAME1 = "FontSelinstin";
        final String SKIPPED_USERNAME2 = "rocuhdjekrt";
        final String SKIPPED_USERNAME3 = "kauiwoeieurt";
        final String USERNAME4 = "djjerekwerwewer";

        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.TWITTER_TEST_ORIGIN_NAME);
        String body = "Starting post @ #ThisIsTagofsome-event-and entertainment"
                + " by @" + USERNAME1 + " @@" + SKIPPED_USERNAME2 + " @#" + SKIPPED_USERNAME3
                + " &amp; @" + USERNAME4
                + " https://t.co/djkdfeowefPh";
        List<MbUser> users = MbUser.fromOriginAndUserOid(origin.getId(), "").fromBodyText(body, false);
        String msgLog = body + " -> " + users;
        assertEquals(msgLog, 2, users.size());
        assertEquals(msgLog, USERNAME1, users.get(0).getUserName());
        assertFalse(msgLog, users.get(0).isOidReal());
        assertFalse(msgLog, users.get(0).hasAltTempOid());

        assertEquals(msgLog, USERNAME4, users.get(1).getUserName());
    }

    public void testIsWebFingerIdValid() {
        checkWebFingerId("", false);
        checkWebFingerId("someUser.", false);
        checkWebFingerId("someUser ", false);
        checkWebFingerId("some.user", false);
        checkWebFingerId("some.user@example.com", true);
        checkWebFingerId("t131t@identi.ca/PumpIo", false);
        checkWebFingerId("some@example.com.", false);
        checkWebFingerId("some@user", false);
    }

    private void checkWebFingerId(String userName, boolean valid) {
        assertEquals("Username '" + userName + "' " + (valid ? "is valid" : "invalid"), valid,
                MbUser.isWebFingerIdValid(userName));
    }
}
