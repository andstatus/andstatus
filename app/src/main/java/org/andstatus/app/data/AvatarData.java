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

package org.andstatus.app.data;

import android.net.Uri;

import org.andstatus.app.database.UserTable;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.UriUtils;

public class AvatarData extends DownloadData {
    public static final String TAG = AvatarData.class.getSimpleName();

    public static void asyncRequestDownload(final long userIdIn) {
        AsyncTaskLauncher.execute(TAG, false,
                new MyAsyncTask<Void, Void, Void>(TAG + userIdIn, MyAsyncTask.PoolEnum.FILE_DOWNLOAD) {
                    @Override
                    protected Void doInBackground2(Void... params) {
                        getForUser(userIdIn).requestDownload();
                        return null;
                    }
                }
        );
    }
    
    public static AvatarData getForUser(long userIdIn) {
        Uri avatarUriNew = UriUtils.fromString(MyQuery.userIdToStringColumnValue(UserTable.AVATAR_URL, userIdIn));
        AvatarData data = new AvatarData(userIdIn, Uri.EMPTY);
        if (!data.getUri().equals(avatarUriNew)) {
            deleteAllOfThisUser(userIdIn);
            data = new AvatarData(userIdIn, avatarUriNew);
        }
        return data;
    }
    
    private AvatarData(long userIdIn, Uri avatarUriNew) {
        super(userIdIn, 0, MyContentType.IMAGE, avatarUriNew);
    }
    
}
