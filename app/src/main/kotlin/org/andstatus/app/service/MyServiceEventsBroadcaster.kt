/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.IntentExtra
import org.andstatus.app.MyAction
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.util.MyLog

class MyServiceEventsBroadcaster private constructor(private val mMyContext: MyContext, private val mState: MyServiceState) {
    private var mCommandData: CommandData = CommandData.EMPTY
    private var mEvent: MyServiceEvent = MyServiceEvent.UNKNOWN
    private var progress: String = ""

    fun setCommandData(commandData: CommandData): MyServiceEventsBroadcaster {
        mCommandData = commandData
        return this
    }

    fun setEvent(serviceEvent: MyServiceEvent): MyServiceEventsBroadcaster {
        mEvent = serviceEvent
        return this
    }

    fun setProgress(text: String?): MyServiceEventsBroadcaster {
        progress = text ?: ""
        return this
    }

    fun broadcast() {
        val intent = MyAction.SERVICE_STATE.getIntent()
        if (mCommandData !== CommandData.EMPTY) {
            mCommandData.getResult().setProgress(progress)
        }
        mCommandData.toIntent(intent)
        intent.putExtra(IntentExtra.SERVICE_STATE.key, mState.save())
        intent.putExtra(IntentExtra.SERVICE_EVENT.key, mEvent.save())
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this) {
                ("state:" + mState + ", event:" + mEvent
                        + ", " + mCommandData.toCommandSummary( MyContextHolder.myContextHolder.getNow())
                        + if (progress.isEmpty()) "" else ", progress:$progress")
            }
        }
        mMyContext.context.sendBroadcast(intent)
    }

    companion object {
        fun newInstance(myContext: MyContext, state: MyServiceState): MyServiceEventsBroadcaster {
            return MyServiceEventsBroadcaster(myContext, state)
        }
    }
}
