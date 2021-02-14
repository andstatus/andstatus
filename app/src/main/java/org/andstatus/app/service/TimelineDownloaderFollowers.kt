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

package org.andstatus.app.service;

import org.andstatus.app.net.social.ApiRoutineEnum;

import io.vavr.control.Try;

class TimelineDownloaderFollowers extends TimelineDownloader {

    TimelineDownloaderFollowers(CommandExecutionContext execContext) {
        super(execContext);
    }

    @Override
    public Try<Boolean> download() {
        CommandExecutorStrategy strategy = new CommandExecutorFollowers(execContext);
        return strategy.execute();
    }

    @Override
    boolean isApiSupported(ApiRoutineEnum routine) {
        return super.isApiSupported(routine) || super.isApiSupported(getAlternativeApiRoutine(routine));
    }

    private ApiRoutineEnum getAlternativeApiRoutine(ApiRoutineEnum routine) {
        switch (routine) {
            case GET_FOLLOWERS:
                return ApiRoutineEnum.GET_FOLLOWERS_IDS;
            case GET_FOLLOWERS_IDS:
                return ApiRoutineEnum.GET_FOLLOWERS;
            case GET_FRIENDS:
                return ApiRoutineEnum.GET_FRIENDS_IDS;
            case GET_FRIENDS_IDS:
                return ApiRoutineEnum.GET_FRIENDS;
            default:
                return ApiRoutineEnum.DUMMY_API;
        }

    }
}
