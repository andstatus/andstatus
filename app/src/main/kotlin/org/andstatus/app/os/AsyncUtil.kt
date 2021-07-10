/*
 * Copyright (c) 2016-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.os

import android.os.Looper

object AsyncUtil {
    const val MAX_COMMAND_EXECUTION_SECONDS: Long = 600

    val nonUiThread: Boolean get() = !isUiThread

    // See http://stackoverflow.com/questions/11411022/how-to-check-if-current-thread-is-not-main-thread
    val isUiThread: Boolean get() = Looper.myLooper() == Looper.getMainLooper()

}
