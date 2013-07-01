/*
* Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
* Based on the sample: com.example.android.samplesync.syncadapter
* Copyright (C) 2010 The Android Open Source Project
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

package org.andstatus.app.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

import org.andstatus.app.CommandData;
import org.andstatus.app.MyService;
import org.andstatus.app.MyService.CommandEnum;
import org.andstatus.app.MyServiceManager;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyDatabase.TimelineTypeEnum;
import org.andstatus.app.util.MyLog;

/**
 * SyncAdapter implementation. Its only purpose for now is to properly initialize {@link MyService}.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    static final String TAG = SyncAdapter.class.getSimpleName();

    private final Context mContext;
    
    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        MyPreferences.initialize(mContext, this);
        MyLog.d(TAG, "created, context=" + context.getClass().getCanonicalName());
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        try {
            boolean ignoreAlarms = MyServiceManager.isIgnoreAlarms();
            MyLog.d(TAG, "onPerformSync started, account=" + account.name
                    + (ignoreAlarms ? "; ignoring" : ""));

            if (!ignoreAlarms) {
                /** Send the command to {@link MyService} */
                CommandData element = new CommandData(CommandEnum.AUTOMATIC_UPDATE, account.name,
                        TimelineTypeEnum.ALL, 0);
                mContext.sendBroadcast(element.toIntent());
            }

            // TODO: Wait till the actual completion in order to give
            // feedback to the user about a duration and a result of the sync
        } finally {

        }
    }
}
