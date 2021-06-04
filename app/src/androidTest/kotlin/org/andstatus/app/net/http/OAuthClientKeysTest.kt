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
package org.andstatus.app.net.http

import org.andstatus.app.account.AccountConnectionData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class OAuthClientKeysTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.forget()
        TestSuite.initialize(this)
    }

    @Test
    fun testKeysSave() {
        val connectionData: HttpConnectionData = HttpConnectionData.Companion.fromAccountConnectionData(
                AccountConnectionData.Companion.fromMyAccount( MyContextHolder.myContextHolder.getNow().accounts.getFirstPreferablySucceededForOrigin(
                         MyContextHolder.myContextHolder.getNow().origins.firstOfType(OriginType.PUMPIO)), TriState.UNKNOWN)
        )
        val consumerKey = "testConsumerKey" + System.nanoTime().toString()
        val consumerSecret = "testConsumerSecret" + System.nanoTime().toString()
        connectionData.originUrl = UrlUtils.fromString("https://example.com")
        val keys1: OAuthClientKeys = OAuthClientKeys.Companion.fromConnectionData(connectionData)
        keys1.clear()
        Assert.assertEquals("Keys are cleared", false, keys1.areKeysPresent())
        keys1.setConsumerKeyAndSecret(consumerKey, consumerSecret)
        val keys2: OAuthClientKeys = OAuthClientKeys.Companion.fromConnectionData(connectionData)
        Assert.assertEquals("Keys are loaded", true, keys2.areKeysPresent())
        Assert.assertEquals(consumerKey, keys2.getConsumerKey())
        Assert.assertEquals(consumerSecret, keys2.getConsumerSecret())
        keys2.clear()
        val keys3: OAuthClientKeys = OAuthClientKeys.Companion.fromConnectionData(connectionData)
        Assert.assertEquals("Keys are cleared", false, keys3.areKeysPresent())
    }
}
