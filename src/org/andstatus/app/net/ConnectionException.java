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

import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Yuri Volkov
 */
public class ConnectionException extends Exception {
    public enum StatusCode {
        UNKNOWN,
        NOT_FOUND
    }
    private StatusCode statusCode = StatusCode.UNKNOWN;

    public static ConnectionException loggedJsonException(String TAG, JSONException e, JSONObject jso, String detailMessage) throws ConnectionException {
        MyLog.d(TAG, detailMessage + ": " + e.getMessage());
        if (jso != null) {
            try {
                MyLog.v(TAG, "jso: " + jso.toString(4));
            } catch (JSONException e1) {}
        }
        return new ConnectionException(detailMessage);
    }
    
	/**
	 * @param detailMessage
	 */
	public ConnectionException(String detailMessage) {
		super(detailMessage);
	}

    public ConnectionException(int statusCodeHttp, final String detailMessage) {
        super(detailMessage);
        if (statusCodeHttp == 404) {
            statusCode = StatusCode.NOT_FOUND;
        }
    }

    public ConnectionException(StatusCode statusCode, final String detailMessage) {
        super(detailMessage);
        this.statusCode = statusCode;
    }

    public StatusCode getStatusCode() {
        return this.statusCode;
    }
	
	public ConnectionException(Throwable throwable) {
		super(throwable);
	}

	public ConnectionException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

    @Override
    public String toString() {
        String str = super.toString();
        if (this.statusCode.equals(StatusCode.UNKNOWN)) {
            str = this.statusCode + " " + str;
        }
        return str;
    }

}
