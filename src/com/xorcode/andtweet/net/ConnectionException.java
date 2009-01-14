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

package com.xorcode.andtweet.net;

/**
 * @author torgny.bjers
 *
 */
public class ConnectionException extends Exception {

	private static final long serialVersionUID = 3072410275455642785L;

	/**
	 * @param detailMessage
	 */
	public ConnectionException(String detailMessage) {
		super(detailMessage);
	}

	/**
	 * @param mThrowable
	 */
	public ConnectionException(Throwable throwable) {
		super(throwable);
	}

	/**
	 * @param mDetailMessage
	 * @param mThrowable
	 */
	public ConnectionException(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
	}

}
