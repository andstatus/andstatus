/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.http;

import org.andstatus.app.util.MyLog;

import java.io.IOException;
import java.net.URL;

/**
 * @author yvolk@yurivolkov.com
 */
public class ConnectionException extends IOException {
    private static final long serialVersionUID = 1L;

    public enum StatusCode {
        UNKNOWN,
        OK,
        UNSUPPORTED_API,
        NOT_FOUND,
        BAD_REQUEST,
        AUTHENTICATION_ERROR,
        CREDENTIALS_OF_OTHER_ACCOUNT,
        NO_CREDENTIALS_FOR_HOST, 
        UNAUTHORIZED, 
        FORBIDDEN, INTERNAL_SERVER_ERROR, BAD_GATEWAY, SERVICE_UNAVAILABLE, MOVED,
        REQUEST_ENTITY_TOO_LARGE,
        LENGTH_REQUIRED,
        CLIENT_ERROR,
        SERVER_ERROR;
        
        public static StatusCode fromResponseCode(int responseCode) {
            switch (responseCode) {
	            case 200:
	            case 304:
	            	return OK;
                case 301:
                case 302:
                case 303:
                case 307:
                    return MOVED;
                case 400:
                    return BAD_REQUEST;
                case 401:
                    return UNAUTHORIZED;
                case 403:
                    return FORBIDDEN;
                case 404:
                    return NOT_FOUND;
                case 411:
                    return LENGTH_REQUIRED;
                case 413:
                    return REQUEST_ENTITY_TOO_LARGE;
                case 500:
                    return INTERNAL_SERVER_ERROR;
                case 502:
                    return BAD_GATEWAY;
                case 503:
                    return SERVICE_UNAVAILABLE;
                default:
                    if (responseCode >= 500) {
                        return SERVER_ERROR;
                    } else if (responseCode >= 400) {
                        return CLIENT_ERROR;
                    }
                    return UNKNOWN;
            }
        }
    }
    private final StatusCode statusCode;
    private final boolean isHardError;
    private final URL host;

    public static ConnectionException loggedHardJsonException(Object objTag, String detailMessage, Exception e, Object jso) {
        return loggedJsonException(objTag, detailMessage, e, jso, true);
    }
    
    public static ConnectionException loggedJsonException(Object objTag, String detailMessage, Exception e, Object jso) {
        return loggedJsonException(objTag, detailMessage, e, jso, false);
    }

    private static ConnectionException loggedJsonException(Object objTag, String detailMessage, Exception e, Object jso, 
            boolean isHard) {
        MyLog.d(objTag, detailMessage + (e != null ? ": " + e.getMessage() : ""));
        if (jso != null) {
            if (e != null) {
                String stackTrace = MyLog.getStackTrace(e);
                MyLog.writeStringToFile(stackTrace, MyLog.uniqueDateTimeFormatted() + "_JsonException_stacktrace_log.txt");
                MyLog.v(objTag, "stack trace: " + stackTrace);
            }
            MyLog.logJson(objTag, "json_exception", jso, true);
        }
        return new ConnectionException(StatusCode.OK, MyLog.objToTag(objTag) + ": " + detailMessage, e, null, isHard);
    }

    public static ConnectionException fromStatusCode(StatusCode statusCode, final String detailMessage) {
        return fromStatusCodeAndThrowable(statusCode, detailMessage, null);
    }

    public static ConnectionException fromStatusCodeAndThrowable(StatusCode statusCode, final String detailMessage, Throwable throwable) {
        return new ConnectionException(statusCode, detailMessage, throwable, null, false);
    }
    
    public static ConnectionException fromStatusCodeAndHost(StatusCode statusCode, final String detailMessage, URL host2) {
        return new ConnectionException(statusCode, detailMessage, host2);
    }

    public static ConnectionException hardConnectionException(String detailMessage, Throwable throwable) {
        return new ConnectionException(StatusCode.OK, detailMessage, throwable, null, true);
    }
    
    public ConnectionException(Throwable throwable) {
        this(null, throwable);
    }

    public ConnectionException(String detailMessage) {
        this(detailMessage, null);
    }

    public ConnectionException(StatusCode statusCode, String detailMessage) {
        this(statusCode, detailMessage, null);
    }
    
    public ConnectionException(String detailMessage, Throwable throwable) {
        this(StatusCode.OK, detailMessage, throwable, null, false);
    }
    
    public ConnectionException(StatusCode statusCode, String detailMessage, URL host) {
        this(statusCode, detailMessage, null, host, false);
    }
    
    private ConnectionException(StatusCode statusCode, String detailMessage, 
            Throwable throwable, URL host, boolean isHardIn) {
        super(detailMessage, throwable);
        this.statusCode = statusCode;
        this.host = host; 
        this.isHardError = isHardFromStatusCode(isHardIn, statusCode);
    }

    private static boolean isHardFromStatusCode(boolean isHardIn, StatusCode statusCode) {
        return isHardIn || (statusCode != StatusCode.UNKNOWN && statusCode != StatusCode.OK);
    }

    public StatusCode getStatusCode() {
        return this.statusCode;
    }

    @Override
    public String toString() {
        return "Status code=" + this.statusCode + "; " + (isHardError ? "hard" : "soft") 
			+ (host == null ? "" : "; host=" + host) 
			+ "; \n" + super.getMessage()
		    + (super.getCause() != null ? "; \nCaused by " + super.getCause().toString() : "");
    }

    public boolean isHardError() {
        return isHardError;
    }
}
