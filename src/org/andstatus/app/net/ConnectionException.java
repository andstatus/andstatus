/* 
 * Copyright (C) 2008 Torgny Bjers
 * Copyright (c) 2011 yvolk (Yuri Volkov), http://yurivolkov.com
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

/**
 * @author torgny.bjers
 *
 */
public class ConnectionException extends Exception {

	private static final long serialVersionUID = 3072410275455642785L;
	/**
	 * Holds status code of HTTP Responses...
	 * 0 means "unknown"
	 */
    private final int statusCode;

	/**
	 * @param detailMessage
	 */
	public ConnectionException(String detailMessage) {
		super(detailMessage);
        this.statusCode = 0;
	}

    public ConnectionException(int statusCode, final String detailMessage) {
        super(detailMessage);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return this.statusCode;
    }
	
	/**
	 * @param throwable
	 */
	public ConnectionException(Throwable throwable) {
		super(throwable);
        this.statusCode = 0;
	}

	/**
	 * @param detailMessage
	 * @param throwable
	 */
	public ConnectionException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
        this.statusCode = 0;
	}

    @Override
    public String toString() {
        String str = super.toString();
        if (this.statusCode != 0) {
            str = Integer.toString(this.statusCode) + " " + str;
        }
        return str;
    }

}
