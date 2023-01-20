/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import io.vavr.control.Try
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.net.social.ConnectionFactory
import org.andstatus.app.net.social.Server
import org.andstatus.app.origin.DiscoveredOrigins
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import java.net.URL

class CommandExecutorGetOpenInstances(execContext: CommandExecutionContext) : CommandExecutorStrategy(execContext) {
    override suspend fun execute(): Try<Boolean> {
        return ConnectionFactory.fromMyAccount(execContext.getMyAccount(), TriState.UNKNOWN)
            .getOpenInstances()
            .map { result -> saveDiscoveredOrigins(result) }
    }

    private fun saveDiscoveredOrigins(result: List<Server>): Boolean {
        val execOrigin = execContext.commandData.getTimeline().origin
        val newOrigins: MutableList<Origin> = ArrayList()
        for (mbOrigin in result) {
            execContext.getResult().incrementDownloadedCount()
            val origin = Origin.Builder(execContext.myContext, execOrigin.originType).setName(mbOrigin.name)
                .setHostOrUrl(mbOrigin.urlString)
                .build()
            if (origin.isValid
                && !myContextHolder.getNow().origins.fromName(origin.name).isValid
                && !haveOriginsWithThisHostName(origin.url)
            ) {
                newOrigins.add(origin)
            } else {
                MyLog.d(this, "Origin is not valid: $origin")
            }
        }
        DiscoveredOrigins.replaceAll(newOrigins)
        return true
    }

    private fun haveOriginsWithThisHostName(url: URL?): Boolean {
        if (url == null) {
            return true
        }
        for (origin in myContextHolder.getNow().origins.collection()) {
            origin.url?.let {
                if (it.host == url.host) return true
            }
        }
        return false
    }
}
