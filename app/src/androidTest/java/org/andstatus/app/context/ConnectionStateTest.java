/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.test.InstrumentationTestCase;

import org.andstatus.app.service.ConnectionRequired;
import org.andstatus.app.service.ConnectionState;

/**
 * @author yvolk@yurivolkov.com
 */
public class ConnectionStateTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }

    public void testIsConnectionStateOkFor() {
        TestSuite.getMyContextForTest().setConnectionState(ConnectionState.OFFLINE);
        assertEquals(ConnectionState.OFFLINE, MyContextHolder.get().getConnectionState());

        assertTrueFor(ConnectionRequired.OFFLINE);
        assertTrueFor(ConnectionRequired.ANY);
        assertFalseFor(ConnectionRequired.SYNC);
        assertFalseFor(ConnectionRequired.DOWNLOAD_ATTACHMENT);

        TestSuite.getMyContextForTest().setConnectionState(ConnectionState.ONLINE);
        assertEquals(ConnectionState.ONLINE, MyContextHolder.get().getConnectionState());

        assertFalseFor(ConnectionRequired.OFFLINE);
        assertTrueFor(ConnectionRequired.ANY);

        MyPreferences.setDownloadAttachmentsOverWiFiOnly(false);
        MyPreferences.setIsSyncOverWiFiOnly(true);
        assertFalseFor(ConnectionRequired.SYNC);
        assertFalseFor(ConnectionRequired.DOWNLOAD_ATTACHMENT);
        MyPreferences.setIsSyncOverWiFiOnly(false);
        assertTrueFor(ConnectionRequired.SYNC);
        assertTrueFor(ConnectionRequired.DOWNLOAD_ATTACHMENT);
        MyPreferences.setDownloadAttachmentsOverWiFiOnly(true);
        assertTrueFor(ConnectionRequired.SYNC);
        assertFalseFor(ConnectionRequired.DOWNLOAD_ATTACHMENT);

        TestSuite.getMyContextForTest().setConnectionState(ConnectionState.WIFI);
        assertFalseFor(ConnectionRequired.OFFLINE);
        assertTrueFor(ConnectionRequired.ANY);
        assertTrueFor(ConnectionRequired.SYNC);
        assertTrueFor(ConnectionRequired.DOWNLOAD_ATTACHMENT);
    }

    private void assertTrueFor(ConnectionRequired connectionRequired) {
        assertTrue(isConnectionStateOkFor(connectionRequired));
    }

    private void assertFalseFor(ConnectionRequired connectionRequired) {
        assertFalse(isConnectionStateOkFor(connectionRequired));
    }

    private boolean isConnectionStateOkFor(ConnectionRequired connectionRequired) {
        return connectionRequired.isConnectionStateOk(MyContextHolder.get().getConnectionState());
    }

}
