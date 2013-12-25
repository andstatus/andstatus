/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.origin;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.net.Connection.ApiEnum;
import org.andstatus.app.net.ConnectionEmpty;
import org.andstatus.app.net.HttpConnectionEmpty;

public class OriginConnectionData {
    public ApiEnum api = ApiEnum.UNKNOWN_API;
    public long originId = 0;
    public boolean isSsl = true;
    public boolean isOAuth = true;
    public String host = "";
    public String basicPath = "";
    public String oauthPath = "oauth";
    
    public String accountUsername = "";
    public String accountUserOid = "";
    public AccountDataReader dataReader = null;
    
    public Class<? extends org.andstatus.app.net.Connection> connectionClass = ConnectionEmpty.class;
    public Class<? extends org.andstatus.app.net.HttpConnection> httpConnectionClass = HttpConnectionEmpty.class;
}
