/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor;
import android.net.Uri;

import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.support.android.v11.app.MyLoaderManager;
import org.andstatus.app.util.SelectionAndArgs;

import java.util.Arrays;

class TimelineListParameters {
    MyLoaderManager.LoaderCallbacks<Cursor> loaderCallbacks = null;
    
    boolean loadOneMorePage = false;
    boolean reQuery = false;
    TimelineTypeEnum timelineType = TimelineTypeEnum.UNKNOWN;
    boolean timelineCombined = false;
    long myAccountUserId = 0;
    long selectedUserId = 0;
    
    String[] projection;
    String searchQuery = "";
    Uri contentUri = null;
    boolean incrementallyLoadingPages = false;
    int rowsLimit = 0;
    long lastItemId = 0;
    volatile SelectionAndArgs sa = new SelectionAndArgs();
    String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;

    // Execution state / data:
    volatile long startTime = 0;
    volatile boolean cancelled = false;
    volatile TimelineTypeEnum timelineToReload = TimelineTypeEnum.UNKNOWN;
    
    public boolean isEmpty() {
        return timelineType == TimelineTypeEnum.UNKNOWN;
    }
    
    @Override
    public String toString() {
        return "TimelineListParameters [loaderCallbacks=" + loaderCallbacks + ", loadOneMorePage="
                + loadOneMorePage + ", reQuery=" + reQuery + ", timelineType=" + timelineType
                + ", timelineCombined=" + timelineCombined + ", myAccountUserId=" + myAccountUserId
                + ", selectedUserId=" + selectedUserId + ", projection="
                + Arrays.toString(projection) + ", searchQuery=" + searchQuery + ", contentUri="
                + contentUri + ", incrementallyLoadingPages=" + incrementallyLoadingPages
                + ", rowsLimit=" + rowsLimit + ", lastItemId=" + lastItemId + ", sa=" + sa
                + ", sortOrder=" + sortOrder + ", startTime=" + startTime + ", cancelled="
                + cancelled + ", timelineToReload=" + timelineToReload + "]";
    }
    
}
