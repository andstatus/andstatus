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
package org.andstatus.app.context

import org.andstatus.app.service.ConnectionRequired
import org.andstatus.app.service.ConnectionState
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class ConnectionStateTest {
    @Before
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testIsConnectionStateOkFor() {
        TestSuite.getMyContextForTest().connectionState = ConnectionState.OFFLINE
        Assert.assertEquals(ConnectionState.OFFLINE, MyContextHolder.myContextHolder.getNow().connectionState)
        assertTrueFor(ConnectionRequired.OFFLINE)
        assertTrueFor(ConnectionRequired.ANY)
        assertFalseFor(ConnectionRequired.SYNC)
        assertFalseFor(ConnectionRequired.DOWNLOAD_ATTACHMENT)
        TestSuite.getMyContextForTest().connectionState = ConnectionState.ONLINE
        Assert.assertEquals(ConnectionState.ONLINE, MyContextHolder.myContextHolder.getNow().connectionState)
        assertFalseFor(ConnectionRequired.OFFLINE)
        assertTrueFor(ConnectionRequired.ANY)
        MyPreferences.setDownloadAttachmentsOverWiFiOnly(false)
        MyPreferences.setIsSyncOverWiFiOnly(true)
        assertFalseFor(ConnectionRequired.SYNC)
        assertFalseFor(ConnectionRequired.DOWNLOAD_ATTACHMENT)
        MyPreferences.setIsSyncOverWiFiOnly(false)
        assertTrueFor(ConnectionRequired.SYNC)
        assertTrueFor(ConnectionRequired.DOWNLOAD_ATTACHMENT)
        MyPreferences.setDownloadAttachmentsOverWiFiOnly(true)
        assertTrueFor(ConnectionRequired.SYNC)
        assertFalseFor(ConnectionRequired.DOWNLOAD_ATTACHMENT)
        TestSuite.getMyContextForTest().connectionState = ConnectionState.WIFI
        assertFalseFor(ConnectionRequired.OFFLINE)
        assertTrueFor(ConnectionRequired.ANY)
        assertTrueFor(ConnectionRequired.SYNC)
        assertTrueFor(ConnectionRequired.DOWNLOAD_ATTACHMENT)
    }

    private fun assertTrueFor(connectionRequired: ConnectionRequired) {
        Assert.assertTrue(isConnectionStateOkFor(connectionRequired))
    }

    private fun assertFalseFor(connectionRequired: ConnectionRequired) {
        Assert.assertFalse(isConnectionStateOkFor(connectionRequired))
    }

    private fun isConnectionStateOkFor(connectionRequired: ConnectionRequired): Boolean {
        return connectionRequired.isConnectionStateOk(MyContextHolder.myContextHolder.getNow().connectionState)
    }
}
