/*
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import io.vavr.control.Try
import org.andstatus.app.net.social.ApiRoutineEnum

internal class TimelineDownloaderFollowers(execContext: CommandExecutionContext) : TimelineDownloader(execContext) {
    override suspend fun download(): Try<Boolean> {
        val strategy: CommandExecutorStrategy = CommandExecutorFollowers(execContext)
        return strategy.execute()
    }

    override fun isApiSupported(routine: ApiRoutineEnum): Boolean {
        return super.isApiSupported(routine) || super.isApiSupported(getAlternativeApiRoutine(routine))
    }

    private fun getAlternativeApiRoutine(routine: ApiRoutineEnum?): ApiRoutineEnum {
        return when (routine) {
            ApiRoutineEnum.GET_FOLLOWERS -> ApiRoutineEnum.GET_FOLLOWERS_IDS
            ApiRoutineEnum.GET_FOLLOWERS_IDS -> ApiRoutineEnum.GET_FOLLOWERS
            ApiRoutineEnum.GET_FRIENDS -> ApiRoutineEnum.GET_FRIENDS_IDS
            ApiRoutineEnum.GET_FRIENDS_IDS -> ApiRoutineEnum.GET_FRIENDS
            else -> ApiRoutineEnum.DUMMY_API
        }
    }
}
