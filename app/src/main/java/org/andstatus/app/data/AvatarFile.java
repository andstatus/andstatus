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

import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.graphics.MyDrawableCache;
import org.andstatus.app.graphics.MyImageCache;

public class AvatarFile {
    private final long userId;
    private final DownloadFile downloadFile;
    public static final int AVATAR_SIZE_DIP = 48;
    
    public AvatarFile(long userIdIn, String filename) {
        userId = userIdIn;
        downloadFile = new DownloadFile(filename);
    }

    public static Drawable getDefaultDrawable() {
        return MyImageCache.getStyledDrawable(R.drawable.ic_person_black_36dp, R.drawable.ic_person_white_36dp);
    }

    @NonNull
    public static Drawable getDrawable(long authorId, Cursor cursor) {
        Drawable drawable = null;
        if (MyPreferences.getShowAvatars()) {
            String avatarFilename = DbUtils.getString(cursor, MyDatabase.Download.AVATAR_FILE_NAME);
            AvatarFile avatarFile = new AvatarFile(authorId, avatarFilename);
            drawable = avatarFile.getDrawable();
        }
        if (drawable == null) {
            drawable = getDefaultDrawable();
        }
        return drawable;
    }

    @NonNull
    public Drawable getDrawable() {
        Drawable drawable = MyImageCache.getAvatarDrawable(this, downloadFile.getFilePath());
        if (drawable == MyDrawableCache.BROKEN) {
            return getDefaultDrawable();
        } else if (drawable != null) {
            return drawable;
        }
        if (!downloadFile.exists()) {
            AvatarData.asyncRequestDownload(userId);
        }
        return getDefaultDrawable();
    }

    @Override
    public String toString() {
        return "AvatarFile [userId=" + userId + ", " + downloadFile + "]";
    }

}
