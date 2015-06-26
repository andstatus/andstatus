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

package org.andstatus.app.data;

import android.content.SearchRecentSuggestionsProvider;

import org.andstatus.app.ClassInApplicationPackage;

/**
 * TODO: Extend as in http://www.grokkingandroid.com/android-tutorial-adding-suggestions-to-search/
 */
public class TimelineSearchSuggestionsProvider extends SearchRecentSuggestionsProvider {
    /** Note: This is historical constant, remained to preserve compatibility without reinstallation */
    public static final String AUTHORITY = ClassInApplicationPackage.PACKAGE_NAME + ".data.TimelineSuggestionProvider";
    public static final String DATABASE_NAME = "suggestions.db";
    public static final int MODE = DATABASE_MODE_QUERIES;
    
    public TimelineSearchSuggestionsProvider() {
        super();
        setupSuggestions(AUTHORITY, MODE);
    }
}
