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

package org.andstatus.app.data;

import android.content.SearchRecentSuggestionsProvider;

/**
 * @author torgny.bjers
 *
 */
public class TimelineSearchSuggestionProvider extends SearchRecentSuggestionsProvider {

	public final static String AUTHORITY = "org.andstatus.app.data.TimelineSuggestionProvider";
	public final static int MODE = DATABASE_MODE_QUERIES;

	/**
	 * 
	 */
	public TimelineSearchSuggestionProvider() {
		super();
    	setupSuggestions(AUTHORITY, MODE);
	}
}
