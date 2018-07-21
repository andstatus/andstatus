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

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Connection;

class TimelineDownloaderFollowers extends TimelineDownloader {

    TimelineDownloaderFollowers(CommandExecutionContext execContext) {
        super(execContext);
    }

    @Override
    public void download() throws ConnectionException {
        CommandExecutorStrategy strategy = new CommandExecutorFollowers(execContext);
        strategy.execute();
    }

    @Override
    boolean isApiSupported(Connection.ApiRoutineEnum routine) {
        return super.isApiSupported(routine) || super.isApiSupported(getAlternativeApiRputineEnum(routine));
    }

    private Connection.ApiRoutineEnum getAlternativeApiRputineEnum(Connection.ApiRoutineEnum routine) {
        switch (routine) {
            case GET_FOLLOWERS:
                return Connection.ApiRoutineEnum.GET_FOLLOWERS_IDS;
            case GET_FOLLOWERS_IDS:
                return Connection.ApiRoutineEnum.GET_FOLLOWERS;
            case GET_FRIENDS:
                return Connection.ApiRoutineEnum.GET_FRIENDS_IDS;
            case GET_FRIENDS_IDS:
                return Connection.ApiRoutineEnum.GET_FRIENDS;
            default:
                return Connection.ApiRoutineEnum.DUMMY;
        }

    }
}
