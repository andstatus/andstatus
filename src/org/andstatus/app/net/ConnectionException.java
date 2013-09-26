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

import android.text.TextUtils;

import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author yvolk@yurivolkov.com
 */
public class ConnectionException extends Exception {
    private static final long serialVersionUID = 1L;

    public enum StatusCode {
        UNKNOWN,
        UNSUPPORTED_API,
        NOT_FOUND,
        BAD_REQUEST,
        AUTHENTICATION_ERROR,
        CREDENTIALS_OF_OTHER_USER,
        NO_CREDENTIALS_FOR_HOST;
        
        public static StatusCode fromResponseCode(int responseCode) {
            switch (responseCode) {
                case 404:
                    return NOT_FOUND;
                case 400:
                    return BAD_REQUEST;
                default:
                    return UNKNOWN;
            }
        }
    }
    private StatusCode statusCode = StatusCode.UNKNOWN;
    private boolean isHardError = false;
    private String host = "";

    public static ConnectionException loggedJsonException(String TAG, JSONException e, JSONObject jso, String detailMessage) throws ConnectionException {
        MyLog.d(TAG, detailMessage + ": " + e.getMessage());
        if (jso != null) {
            try {
                MyLog.v(TAG, "jso: " + jso.toString(4));
            } catch (JSONException e1) {}
        }
        return new ConnectionException(detailMessage);
    }

    public static ConnectionException fromStatusCodeHttp(int statusCodeHttp, final String detailMessage) {
        StatusCode statusCode = StatusCode.fromResponseCode(statusCodeHttp);
        if (statusCode == StatusCode.UNKNOWN) {
            return new ConnectionException(detailMessage);
        } else {
            return new ConnectionException(statusCode, detailMessage);
        }
    }

    public static ConnectionException fromStatusCodeAndHost(StatusCode statusCode, String host, final String detailMessage) {
        ConnectionException e = new ConnectionException(statusCode, detailMessage);
        e.host = host;
        return e;
    }
    
	public ConnectionException(String detailMessage) {
		super(detailMessage);
	}

    public ConnectionException(StatusCode statusCode, final String detailMessage) {
        super(detailMessage);
        this.statusCode = statusCode;
        switch (statusCode) {
            case UNKNOWN:
                break;
            default:
                isHardError = true;
                break;
        }
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
        return this.statusCode + (isHardError ? "; hard" : "; soft") + (TextUtils.isEmpty(host) ? "" : "; host=" + host) + "; " + super.toString();
    }

    public void setHardError(boolean isHardError) {
        this.isHardError = isHardError;
    }

    public boolean isHardError() {
        return isHardError;
    }
    
    public String getHost() {
        return host;
    }
}
