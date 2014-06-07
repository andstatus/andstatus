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

import android.content.Context;
import android.graphics.drawable.Drawable;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

public class AvatarDrawable {
    private final long userId;
    private final AvatarFile avatarFile;
    public static final int AVATAR_SIZE_DIP = 48;
    
    private final static Drawable DEFAULT_AVATAR = loadDefaultAvatar();
    
    public AvatarDrawable(long userIdIn, String fileName) {
        userId = userIdIn;
        avatarFile = new AvatarFile(fileName);
    }
    
    private static Drawable loadDefaultAvatar() {
        Drawable avatar = null;
        MyLog.v(AvatarDrawable.class, "Loading default avatar");
        Context context = MyContextHolder.get().context();
        if (context != null) {
            avatar = context.getResources().getDrawable(R.drawable.avatar_default);
        }
        return avatar;
    }

    public Drawable getDefaultDrawable() {
        return DEFAULT_AVATAR;
    }
    
    public Drawable getDrawable() {
        if (avatarFile.exists()) {
            return Drawable.createFromPath(avatarFile.getFile().getAbsolutePath());
        }
        new AvatarData(userId).requestDownload();
        return DEFAULT_AVATAR;
    }

    @Override
    public String toString() {
        return "AvatarDrawable [userId=" + userId + ", " + avatarFile + "]";
    }
}
