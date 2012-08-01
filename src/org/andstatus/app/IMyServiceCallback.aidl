/*
 * Copyright (c) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

/**
 * Callback interface used by IMyServiceCallback to send
 * synchronous notifications back to its clients.
 */
oneway interface IMyServiceCallback {
    /**
     * Called when the service has found new tweets.
     */
    void tweetsChanged(int value);

    /**
     * Called when the service has found new messages.
     */
    void messagesChanged(int value);

    /**
     * Called when the service has found replies.
     */
    void repliesChanged(int value);

	/**
	 * Called when the service has completed loading data
	 * @param value doesn't have any meaning yet
	 */
	void dataLoading(int value);

	/**
	 * Called when the service got rateLimitStatus.
	 */
	void rateLimitStatus(int remaining_hits, int hourly_limit);
}
