/* 
 * Copyright (C) 2008 Torgny Bjers
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
public class ConnectionUnavailableException extends Exception {

	private static final long serialVersionUID = -4610607594776171179L;

	/**
	 * @param mDetailMessage
	 */
	public ConnectionUnavailableException(String detailMessage) {
		super(detailMessage);
	}

	/**
	 * @param mThrowable
	 */
	public ConnectionUnavailableException(Throwable throwable) {
		super(throwable);
	}

	/**
	 * @param mDetailMessage
	 * @param mThrowable
	 */
	public ConnectionUnavailableException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}
}
