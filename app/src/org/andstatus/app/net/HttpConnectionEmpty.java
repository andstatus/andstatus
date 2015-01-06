/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public class HttpConnectionEmpty extends HttpConnection {

    @Override
    protected JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException {
        return null;
    }

    @Override
    protected JSONObject postRequest(String path) throws ConnectionException {
        return null;
    }

    @Override
    protected JSONObject getRequest(String path) throws ConnectionException {
        return null;
    }

    @Override
    protected JSONArray getRequestAsArray(String path) throws ConnectionException {
        return null;
    }

    @Override
    public void clearAuthInformation() {
        // Empty
    }

    @Override
    public boolean getCredentialsPresent() {
        return false;
    }

    @Override
    public void downloadFile(String url, File file) {
        // Empty
    }

}
