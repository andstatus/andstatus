/**
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.net.http

import org.andstatus.app.util.MyLog

/**
 * These are the keys for the AndStatus application (a "Client" of the Microblogging system)
 * Keys are per Microblogging System (i.e. per the "Origin")
 *
 * You may use this application:
 * 1. With application OAuth keys provided in the public repository and stored in the [OAuthClientKeysOpenSource] class
 * 2. With keys of your own (Twitter etc.) application.
 *
 * Instructions:
 * 1. Leave everything as is.
 * Please read this information about possible problems with Twitter:
 * [http://blog.nelhage.com/2010/09/dear-twitter/](http://blog.nelhage.com/2010/09/dear-twitter/).
 * 2. Create new class in this package with this name: [OAuthClientKeys.SECRET_CLASS_NAME]
 * as a copy of existing [OAuthClientKeysOpenSource] class
 * with Your application's  Consumer Key and Consumer Secret
 *
 * For more information please read
 * [Developer FAQ](https://github.com/andstatus/andstatus/wiki/Developerfaq).
 *
 * Strategy pattern, see http://en.wikipedia.org/wiki/Strategy_pattern
 */
class OAuthClientKeys private constructor(private val strategy: OAuthClientKeysStrategy) {

    val areDynamic = strategy is OAuthClientKeysDynamic

    fun areKeysPresent(): Boolean {
        return getConsumerKey().isNotEmpty() && !getConsumerSecret().isEmpty()
    }

    fun getConsumerKey(): String {
        return strategy.getConsumerKey()
    }

    fun getConsumerSecret(): String {
        return strategy.getConsumerSecret()
    }

    fun clear() {
        setConsumerKeyAndSecret("", "")
    }

    fun setConsumerKeyAndSecret(consumerKey: String?, consumerSecret: String?) {
        strategy.setConsumerKeyAndSecret(consumerKey, consumerSecret)
        MyLog.d(TAG, "Saved " + strategy.toString() + "; "
                + if (areKeysPresent()) "Keys present" else "No keys"
        )
    }

    companion object {
        private val TAG: String = OAuthClientKeys::class.simpleName!!
        private val SECRET_CLASS_NAME: String = OAuthClientKeysOpenSource::class.java.getPackage()?.getName() +
                "." + "OAuthClientKeysSecret"
        private var noSecretClass = false

        fun fromConnectionData(connectionData: HttpConnectionData): OAuthClientKeys {
            // Load keys published in the public source code repository
            var strategy: OAuthClientKeysStrategy = OAuthClientKeysOpenSource()

            if (!noSecretClass) {
                // Try to load the application's secret keys first
                try {
                    val cls = Class.forName(SECRET_CLASS_NAME)
                    strategy = cls.newInstance() as OAuthClientKeysStrategy
                } catch (e: Exception) {
                    MyLog.v(TAG, "Class $SECRET_CLASS_NAME was not loaded", e)
                    noSecretClass = true
                }
            }
            var keys = OAuthClientKeys(strategy)

            keys.strategy.initialize(connectionData)
            if (!keys.areKeysPresent()) {
                keys = OAuthClientKeys(OAuthClientKeysDynamic())
                keys.strategy.initialize(connectionData)
            }
            MyLog.d(TAG, "Loaded " + keys.strategy.toString() + "; "
                    + if (keys.areKeysPresent()) "Keys present" else "No keys"
            )
            return keys
        }
    }
}
