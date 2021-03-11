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
package org.andstatus.app.data

import android.net.Uri
import org.andstatus.app.net.social.Actor

class AvatarData private constructor(actorIdIn: Long, avatarUriNew: Uri?) :
        DownloadData(null, 0, actorIdIn, 0,
                MyContentType.UNKNOWN, "", DownloadType.AVATAR, avatarUriNew) {
    companion object {
        val TAG: String = AvatarData::class.java.simpleName
        fun getCurrentForActor(actor: Actor): AvatarData {
            return AvatarData(actor.actorId, actor.getAvatarUri())
        }

        fun getDisplayedForActor(actor: Actor): AvatarData {
            return AvatarData(actor.actorId, Uri.EMPTY)
        }
    }
}