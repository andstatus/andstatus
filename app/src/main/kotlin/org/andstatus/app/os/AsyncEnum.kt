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

import org.andstatus.app.os.AsyncUtil.MAX_COMMAND_EXECUTION_SECONDS

enum class AsyncEnum(val corePoolSize: Int, val maxCommandExecutionSeconds: Long) {
    SYNC(0, MAX_COMMAND_EXECUTION_SECONDS),
    FILE_DOWNLOAD(0, MAX_COMMAND_EXECUTION_SECONDS),
    QUICK_UI(2, 20),
    DEFAULT_POOL(0, MAX_COMMAND_EXECUTION_SECONDS)
}
