/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.app.Notification;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.data.AssertionData;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.origin.PersistentOrigins;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.timeline.meta.PersistentTimelines;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.IdentifiableInstance;

import java.util.Locale;

public interface MyContext extends IdentifiableInstance {
    MyContext newInitialized(Object initializerName);
    MyContext newCreator(Context context, Object initializerName);
    boolean initialized();
    boolean isReady();
    Locale getLocale();
    MyContextState state();
    Context context();
    String initializedBy();
    long preferencesChangeTime();
    DatabaseHolder getMyDatabase();
    SQLiteDatabase getDatabase();
    @NonNull
    PersistentAccounts persistentAccounts();
    @NonNull
    PersistentOrigins persistentOrigins();
    @NonNull
    PersistentTimelines persistentTimelines();
    void put(AssertionData data);
    void release();
    boolean isExpired();
    void setExpired();
    ConnectionState getConnectionState();
    /** Is our application in Foreground now? **/
    boolean isInForeground();
    void setInForeground(boolean inForeground);
    void notify(TimelineType id, Notification notification);
    void clearNotification(TimelineType id);

    // For testing
    boolean isTestRun();
    HttpConnection getHttpConnectionMock();
}
