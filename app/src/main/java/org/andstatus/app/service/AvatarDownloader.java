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

package org.andstatus.app.service;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.AvatarData;
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.MyLog;

public class AvatarDownloader extends FileDownloader {
    public AvatarDownloader(long userIdIn) {
        this(AvatarData.getForUser(userIdIn));
    }

    protected AvatarDownloader(DownloadData data) {
        super(data);
    }

    @Override
    protected MyAccount findBestAccountForDownload() {
        long originId = MyQuery.userIdToLongColumnValue(MyDatabase.User.ORIGIN_ID, data.userId);
        return MyContextHolder.get().persistentAccounts().findFirstSucceededMyAccountByOriginId(originId);
    }

    @Override
    protected void onSuccessfulLoad() {
        data.deleteOtherOfThisUser();
        MyLog.v(this, "Loaded avatar userId:" + data.userId + "; uri:'" + data.getUri() + "'");
    }
}
