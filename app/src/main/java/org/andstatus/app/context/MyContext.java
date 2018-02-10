/*
 * Copyright (C) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.notification.NotificationData;
import org.andstatus.app.notification.Notifier;
import org.andstatus.app.origin.PersistentOrigins;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.timeline.meta.PersistentTimelines;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.IdentifiableInstance;

import java.util.Locale;

public interface MyContext extends IdentifiableInstance {
    MyContext EMPTY = new MyContextImpl(null, null, "static");

    MyContext newInitialized(Object initializer);
    MyContext newCreator(Context context, Object initializer);
    boolean initialized();
    boolean isReady();
    MyContextState state();
    Context context();
    long preferencesChangeTime();
    DatabaseHolder getMyDatabase();
    String getLastDatabaseError();
    SQLiteDatabase getDatabase();
    @NonNull
    PersistentAccounts accounts();
    @NonNull
    PersistentOrigins origins();
    @NonNull
    PersistentTimelines timelines();
    default void putAssertionData(@NonNull String key, @NonNull ContentValues contentValues) {}
    void release();
    boolean isExpired();
    void setExpired();
    ConnectionState getConnectionState();
    /** Is our application in Foreground now? **/
    boolean isInForeground();
    void setInForeground(boolean inForeground);
    Notifier getNotifier();
    void notify(NotificationData data);
    void clearNotification(@NonNull Timeline timeline);
    default boolean isTestRun() {
        return false;
    }
    default HttpConnection getHttpConnectionMock() {
        return null;
    }
}
