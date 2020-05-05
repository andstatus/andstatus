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
import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccounts;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.notification.NotificationData;
import org.andstatus.app.notification.Notifier;
import org.andstatus.app.origin.PersistentOrigins;
import org.andstatus.app.service.CommandQueue;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.timeline.meta.PersistentTimelines;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.user.CachedUsersAndActors;
import org.andstatus.app.util.IdentifiableInstance;

import java.util.function.Supplier;

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
    CachedUsersAndActors users();
    @NonNull
    MyAccounts accounts();
    @NonNull
    PersistentOrigins origins();
    @NonNull
    PersistentTimelines timelines();
    @NonNull
    CommandQueue queues();
    default void putAssertionData(@NonNull String key, @NonNull ContentValues contentValues) {}
    void save(Supplier<String> reason);
    void release(Supplier<String> reason);
    boolean isExpired();
    void setExpired(Supplier<String> reason);
    ConnectionState getConnectionState();
    /** Is our application in Foreground now? **/
    boolean isInForeground();
    void setInForeground(boolean inForeground);
    Notifier getNotifier();
    void notify(NotificationData data);
    void clearNotifications(@NonNull Timeline timeline);
    default boolean isTestRun() {
        return false;
    }
    default HttpConnection getHttpConnectionMock() {
        return null;
    }
    default boolean isEmpty() {
        return context() == null;
    }
    default boolean isEmptyOrExpired() {
        return isEmpty() || isExpired();
    }
    default boolean isConfigChanged() {
        return initialized() && preferencesChangeTime() != MyPreferences.getPreferencesChangeTime();
    }
}
