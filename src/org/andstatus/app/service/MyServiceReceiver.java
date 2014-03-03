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

import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public final class MyServiceReceiver extends BroadcastReceiver {
    static final String TAG = MyServiceReceiver.class.getSimpleName();
    private int instanceId;
    private MyServiceListener listener;

    public MyServiceReceiver(MyServiceListener listener) {
        super();
        this.listener = listener;
        instanceId = InstanceId.next();
        MyLog.v(this, "Created, instanceId=" + instanceId + (listener != null ? "; listener='"
                + listener.toString() + "'" : ""));
    }
    
    public void registerReceiver(Context context) {
        context.registerReceiver(this, new IntentFilter(MyService.ACTION_SERVICE_STATE));
    }

    public void unregisterReceiver(Context context) {
        try {
            context.unregisterReceiver(this);
        } catch (IllegalArgumentException e) {
            MyLog.v(this, e);
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        MyLog.v(this, "onReceive " + intent.toString());
        if (listener != null) {
            listener.onReceive(CommandData.fromIntent(intent));
        }
    }
}