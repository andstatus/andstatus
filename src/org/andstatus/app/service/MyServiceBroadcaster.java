/**
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

package org.andstatus.app.service;

import android.content.Intent;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.util.MyLog;

public class MyServiceBroadcaster {
    private final MyContext mMyContext;
    private final MyServiceState mState;
    private CommandData mCommandData = null;
    private MyServiceEvent mEvent = MyServiceEvent.UNKNOWN;
    
    private MyServiceBroadcaster(MyContext myContext, MyServiceState state) {
        this.mMyContext = myContext;
        this.mState = state;
    }
    
    public static MyServiceBroadcaster newInstance(MyContext myContext, MyServiceState state) {
        return new MyServiceBroadcaster(myContext, state);
    }

    public MyServiceBroadcaster setCommandData(CommandData commandData) {
        this.mCommandData = commandData;
        return this;
    }

    public MyServiceBroadcaster setEvent(MyServiceEvent serviceEvent) {
        this.mEvent = serviceEvent;
        return this;
    }
    
    public void broadcast() {
        Intent intent = new Intent(MyService.ACTION_SERVICE_STATE);
        if (mCommandData != null) {
            intent = mCommandData.toIntent(intent);
        }
        intent.putExtra(IntentExtra.EXTRA_SERVICE_STATE.key, mState.save());
        intent.putExtra(IntentExtra.EXTRA_SERVICE_EVENT.key, mEvent.save());
        mMyContext.context().sendBroadcast(intent);
        MyLog.v(this, "state: " + mState);
    }
}
