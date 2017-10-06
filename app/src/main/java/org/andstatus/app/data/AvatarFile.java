/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.ImageCaches;

public class AvatarFile extends ImageFile {
    public static final AvatarFile EMPTY = new AvatarFile(0, null);
    private final long userId;
    public static final int AVATAR_SIZE_DIP = 48;
    
    public AvatarFile(long userIdIn, String filename) {
        super(filename);
        userId = userIdIn;
    }

    @NonNull
    public static AvatarFile fromCursor(long userId, Cursor cursor, String avatarColumnName) {
        String avatarFilename = DbUtils.getString(cursor, avatarColumnName);
        return new AvatarFile(userId, avatarFilename);
    }

    @Override
    public CacheName getCacheName() {
        return CacheName.AVATAR;
    }

    @Override
    protected long getId() {
        return userId;
    }

    @Override
    public CachedImage getDefaultImage() {
        return ImageCaches.getStyledImage(R.drawable.ic_person_black_36dp, R.drawable.ic_person_white_36dp);
    }

    @Override
    protected void requestAsyncDownload() {
        if (userId != 0) {
            AvatarData.asyncRequestDownload(userId);
        }
    }

    @Override
    protected boolean isDefaultImageRequired() {
        return true;
    }
}
