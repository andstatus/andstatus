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
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.net.social.Actor;

public class AvatarFile extends ImageFile {
    public static final AvatarFile EMPTY = new AvatarFile(Actor.EMPTY, "", MediaMetadata.EMPTY);
    private final Actor actor;
    public static final int AVATAR_SIZE_DIP = 48;
    
    @NonNull
    public static AvatarFile fromCursor(Actor actor, Cursor cursor) {
        final String filename = DbUtils.getString(cursor, DownloadTable.AVATAR_FILE_NAME);
        return actor.isEmpty()
                ? AvatarFile.EMPTY
                : new AvatarFile(actor, filename, MediaMetadata.EMPTY);
    }

    private AvatarFile(Actor actor, String filename, MediaMetadata mediaMetadata) {
        super(filename, mediaMetadata, 0);
        this.actor = actor;
    }

    @Override
    public CacheName getCacheName() {
        return CacheName.AVATAR;
    }

    @Override
    public long getId() {
        return getActor().actorId;
    }

    public Actor getActor() {
        return actor == null ? Actor.EMPTY : actor;
    }

    @Override
    public CachedImage getDefaultImage() {
        return ImageCaches.getStyledImage(R.drawable.ic_person_black_36dp, R.drawable.ic_person_white_36dp);
    }

    @Override
    protected void requestAsyncDownload() {
        AvatarData.asyncRequestDownload(actor);
    }

    @Override
    protected boolean isDefaultImageRequired() {
        return true;
    }
}
