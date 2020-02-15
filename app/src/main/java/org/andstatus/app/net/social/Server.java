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

import org.andstatus.app.util.StringUtil;

/** 
 * Metadata of a (server of a) Social Network (e.g. from a discovery service)
 * @author yvolk@yurivolkov.com
 * */
public class Server {
    public final String name;
    public final String urlString;
    public final long actorsCount;
    public final long notesCount;

    public Server(String name, String urlString, long actorsCount, long notesCount) {
        this.name = name;
        this.urlString = urlString;
        this.actorsCount = actorsCount;
        this.notesCount = notesCount;
    }
    
    public boolean isEmpty() {
        return StringUtil.isEmpty(name) || StringUtil.isEmpty(urlString);
    }
}
