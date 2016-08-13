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
import org.andstatus.app.data.DownloadData;
import org.andstatus.app.data.MessageForAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.MyLog;

public class AttachmentDownloader extends FileDownloader {

    protected AttachmentDownloader(DownloadData data) {
        super(data);
    }

    @Override
    protected MyAccount findBestAccountForDownload() {
        long originId = MyQuery.msgIdToOriginId(data.msgId);
        boolean subscribedFound = false;
        MyAccount bestAccount = MyContextHolder.get().persistentAccounts().getFirstSucceededForOriginId(originId);
        for( MyAccount ma : MyContextHolder.get().persistentAccounts().list()) {
            if(ma.getOriginId() == originId && ma.isValidAndSucceeded()) {
                MessageForAccount msg = new MessageForAccount(data.msgId, originId, ma);
                if(msg.hasPrivateAccess()) {
                    bestAccount = ma;
                    break;
                }
                if(msg.isSubscribed) {
                    bestAccount = ma;
                    subscribedFound = true;
                }
                if(msg.isTiedToThisAccount() && !subscribedFound) {
                    bestAccount = ma;
                }
            }
        }
        return bestAccount;
    }

    @Override
    protected void onSuccessfulLoad() {
        MyLog.v(this, "Loaded attachment " + data);
    }

}
