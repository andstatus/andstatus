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

package org.andstatus.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyAction;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public final class MyServiceEventsReceiver extends BroadcastReceiver {
    private final long mInstanceId = InstanceId.next();
    private final MyServiceEventsListener listener;
    private final MyContext myContext;

    public MyServiceEventsReceiver(MyContext myContext, MyServiceEventsListener listener) {
        super();
        this.myContext = myContext;
        this.listener = listener;
        MyLog.v(this, "Created, instanceId=" + mInstanceId
                + (listener == null ? "" : "; listener=" + MyLog.objToLongTag(listener)));
    }
    
    public void registerReceiver(Context context) {
        context.registerReceiver(this, new IntentFilter(MyAction.SERVICE_STATE.getAction()));
    }

    public void unregisterReceiver(Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            // Thrown when the "Receiver not registered: org.andstatus.app.service.MyServiceReceiver@..."
            MyLog.ignored(this, e);
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        MyServiceEvent event = MyServiceEvent.load(intent.getStringExtra(IntentExtra.SERVICE_EVENT.key));
        if (event == MyServiceEvent.UNKNOWN) {
            return;
        }
        MyLog.v(this, "onReceive " + event + " for " + MyLog.objToLongTag(listener) + ", instanceId:" + mInstanceId);
        listener.onReceive(CommandData.fromIntent(myContext, intent), event);
    }
}