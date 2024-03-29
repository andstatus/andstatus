/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app

import android.content.Intent
import android.net.Uri
import org.andstatus.app.service.MyService
import org.andstatus.app.service.MyServiceManager

enum class MyAction(actionOrSuffix: String) {
    /**
     * This action is used in intent sent to [MyService].
     * This intent will be received by MyService only if it's initialized
     * (and corresponding broadcast receiver registered).
     */
    EXECUTE_COMMAND("EXECUTE_COMMAND"),

    /**
     * Broadcast with this action is being sent by [MyService] to notify of its state.
     * Actually [MyServiceManager] receives it.
     */
    SERVICE_STATE("SERVICE_STATE"),
    VIEW_CONVERSATION("VIEW_CONVERSATION"),
    VIEW_GROUP_MEMBERS("VIEW_GROUP_MEMBERS"),
    VIEW_ACTORS("VIEW_ACTORS"),
    BOOT_COMPLETED("android.intent.action.BOOT_COMPLETED"),
    ACTION_SHUTDOWN("android.intent.action.ACTION_SHUTDOWN"),
    SYNC("SYNC"),
    INITIALIZE_APP("INITIALIZE_APP"),
    SET_DEFAULT_VALUES("SET_DEFAULT_VALUES"),
    CLOSE_ALL_ACTIVITIES("CLOSE_ALL_ACTIVITIES"),
    UNKNOWN("UNKNOWN");

    val action: String = if (actionOrSuffix.contains(".")) actionOrSuffix
        else ClassInApplicationPackage.PACKAGE_NAME + ".action." + actionOrSuffix

    fun newIntent(): Intent {
        return Intent(action)
    }

    fun newIntent(uri: Uri?): Intent {
        return Intent(action, uri)
    }

    companion object {
        fun fromIntent(intent: Intent?): MyAction {
            val action = if (intent == null) "(null)" else intent.action
            for (value in values()) {
                if (value.action == action) {
                    return value
                }
            }
            return UNKNOWN
        }
    }

}
