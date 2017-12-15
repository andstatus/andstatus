/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.service.MyServiceCommandsRunner;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private final Context mContext;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.mContext = context;
        MyLog.v(this, "created, context:" + context.getClass().getCanonicalName());
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {

        if (!MyServiceManager.isServiceAvailable()) {
            MyLog.d(this, account.name + " Service unavailable");
            return;
        }
        final MyContext myContext = MyContextHolder.initialize(mContext, this);
        new MyServiceCommandsRunner(myContext).autoSyncAccount(
                myContext.persistentAccounts().fromAccountName(account.name), syncResult);
    }
}
