/*
* Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyAction
import org.andstatus.app.context.MyContext
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder

/**
 * @author yvolk@yurivolkov.com
 */
class MyServiceEventsReceiver(private val myContext: MyContext, private val listener: MyServiceEventsListener) : BroadcastReceiver() {
    private val mInstanceId = InstanceId.next()
    fun registerReceiver(context: Context) {
        context.registerReceiver(this, IntentFilter(MyAction.SERVICE_STATE.action))
    }

    fun unregisterReceiver(context: Context) {
        try {
            context.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            // Thrown when the "Receiver not registered: org.andstatus.app.service.MyServiceReceiver@..."
            MyLog.ignored(this, e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val event: MyServiceEvent = MyServiceEvent.load(intent.getStringExtra(IntentExtra.SERVICE_EVENT.key) ?: "")
        if (event == MyServiceEvent.UNKNOWN) return
        MyLog.v(this) {
            ("onReceive " + event + " for " + MyStringBuilder.objToTag(listener)
                    + ", instanceId:" + mInstanceId)
        }
        listener.onReceive(CommandData.fromIntent(myContext, intent), event)
    }

    init {
        MyLog.v(this) {
            ("Created, instanceId=" + mInstanceId
                    + ("; listener=" + MyStringBuilder.objToTag(listener)))
        }
    }
}