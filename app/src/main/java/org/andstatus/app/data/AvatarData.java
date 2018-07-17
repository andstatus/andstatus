/*
 * Copyright (C) 2014-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.net.social.Actor;

public class AvatarData extends DownloadData {
    public static final String TAG = AvatarData.class.getSimpleName();

    public static AvatarData getCurrentForActor(Actor actor) {
        return new AvatarData(actor.actorId, actor.getAvatarUri());
    }

    public static AvatarData getDisplayedForActor(Actor actor) {
        return new AvatarData(actor.actorId, Uri.EMPTY);
    }

    private AvatarData(long actorIdIn, Uri avatarUriNew) {
        super(null, 0, actorIdIn, 0, "", DownloadType.AVATAR, avatarUriNew);
    }
    
}
