/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.text.TextUtils;

/** 
 * Microblogging system metadata (e.g. from a Microblogging system's discovery service)
 * @author yvolk@yurivolkov.com
 * */
public class MbOrigin {
    public final String name;
    public final String urlString;
    public final long usersCount;
    public final long messagesCount;

    public MbOrigin(String name, String urlString, long usersCount, long messagesCount) {
        this.name = name;
        this.urlString = urlString;
        this.usersCount = usersCount;
        this.messagesCount = messagesCount;
    }
    
    public boolean isEmpty() {
        return TextUtils.isEmpty(name) || TextUtils.isEmpty(urlString);
    }
}
