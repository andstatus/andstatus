/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.jcip.annotations.ThreadSafe;

import org.andstatus.app.ClassInApplicationPackage;
import org.andstatus.app.account.MyAccounts;
import org.andstatus.app.data.converter.DatabaseConverterController;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.notification.NotificationData;
import org.andstatus.app.notification.Notifier;
import org.andstatus.app.origin.PersistentOrigins;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.timeline.meta.PersistentTimelines;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.user.MyUsers;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

/**
 * Contains global state of the application
 * The objects are effectively immutable
 * @author yvolk@yurivolkov.com
 */
@ThreadSafe
public class MyContextImpl implements MyContext {
    private static volatile boolean inForeground = false;
    private static volatile long inForegroundChangedAt = 0;
    private static final long CONSIDER_IN_BACKGROUND_AFTER_SECONDS = 20;

    final long instanceId = InstanceId.next();

    private volatile MyContextState state = MyContextState.EMPTY;
    private final Context context;
    private final String initializedBy;
    /**
     * When preferences, loaded into this class, were changed
     */
    private volatile long preferencesChangeTime = 0;

    private volatile DatabaseHolder db = null;
    private volatile String lastDatabaseError = "";

    private final MyUsers users = MyUsers.newEmpty(this);
    private final MyAccounts accounts = MyAccounts.newEmpty(this);
    private final PersistentOrigins origins = PersistentOrigins.newEmpty(this);
    private final PersistentTimelines timelines = PersistentTimelines.newEmpty(this);

    private volatile boolean expired = false;
    private final Notifier notifier = new Notifier(this);

    MyContextImpl(MyContextImpl parent, Context context, Object initializer) {
        initializedBy = MyLog.objToTag(initializer);
        this.context = calcContextToUse(parent, context);
        if (parent != null) {
            lastDatabaseError = parent.getLastDatabaseError();
        }
    }

    @Nullable
    private Context calcContextToUse(MyContextImpl parent, Context contextIn) {
        Context context = contextIn == null ? (parent == null ? null : parent.context()) : contextIn;
        if (context == null) return null;

        Context contextToUse = context.getApplicationContext();
        if ( contextToUse == null) {
            MyLog.w(parent, "getApplicationContext is null, trying the context itself: "
                    + context.getClass().getName());
            contextToUse = context;
        }
        // TODO: Maybe we need to determine if the context is compatible, using some Interface...
        // ...but we don't have any yet.
        if (!contextToUse.getClass().getName().contains(ClassInApplicationPackage.PACKAGE_NAME)) {
            MyLog.w(parent, "Incompatible context: " + contextToUse.getClass().getName());
            contextToUse = null;
        }
        return contextToUse;
    }


    @Override
    public MyContext newInitialized(Object initializer) {
        return new MyContextImpl(this, context(), initializer).initialize();
    }

    MyContextImpl initialize() {
        final String method = "initialize";
        if ( context == null) return this;
        if (!Permissions.checkPermission(context, Permissions.PermissionType.GET_ACCOUNTS)) {
            state = MyContextState.NO_PERMISSIONS;
            return this;
        }
        MyLog.v(this, "Starting initialization of " + instanceId + " by " + initializedBy);

        boolean createApplicationData = MyStorage.isApplicationDataCreated() != TriState.TRUE;
        if (createApplicationData) {
            MyLog.i(this, method + " Creating application data");
            MyPreferencesGroupsEnum.setDefaultValues();
            tryToSetExternalStorageOnDataCreation();
        }
        preferencesChangeTime = MyPreferences.getPreferencesChangeTime();
        initializeDatabase(createApplicationData);

        switch (state) {
            case DATABASE_READY:
                origins.initialize();
                if (MyContextHolder.isOnRestore()) {
                    state = MyContextState.RESTORING;
                } else {
                    users.initialize();
                    accounts.initialize();
                    timelines.initialize();
                    ImageCaches.initialize(context());
                    state = MyContextState.READY;
                }
                break;
            default:
                break;
        }
        notifier.load();
        return this;
    }

    private void initializeDatabase(boolean createApplicationData) {
        final String method = "initializeDatabase";
        DatabaseHolder newDb = new DatabaseHolder(context, createApplicationData);
        try {
            state = newDb.checkState();
            if (state() == MyContextState.DATABASE_READY
                    && MyStorage.isApplicationDataCreated() != TriState.TRUE) {
                state = MyContextState.ERROR;
            }
        } catch (SQLiteException | IllegalStateException e) {
            logDatabaseError(method, e);
            state = MyContextState.ERROR;
            newDb.close();
            db = null;
        }
        if (state() == MyContextState.DATABASE_READY) {
            db = newDb;

          /* For testing:
            final SQLiteDatabase db = mDb.getWritableDatabase();
            String sql = "DROP INDEX IF EXISTS idx_activity_message";
            DbUtils.execSQL(db, sql);
            //sql = "CREATE INDEX idx_activity_message ON activity (activity_msg_id) WHERE activity_msg_id != 0";
            sql = "CREATE INDEX idx_activity_message ON activity (activity_msg_id)";
            DbUtils.execSQL(db, sql);
          **/

        }
    }

    private void logDatabaseError(String method, Exception e) {
        MyLog.e(this, method + " Error", e);
        lastDatabaseError = e.getMessage();
    }

    @Override
    public String getLastDatabaseError() {
        return lastDatabaseError;
    }

    private void tryToSetExternalStorageOnDataCreation() {
        boolean useExternalStorage = !Environment.isExternalStorageEmulated()
                && MyStorage.isWritableExternalStorageAvailable(null);
        MyLog.i(this, "External storage is " + (useExternalStorage ? "" : "not") + " used");
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, useExternalStorage);
    }

    @Override
    public String toString() {
        return  MyLog.getInstanceTag(this) + " by " + initializedBy + "; state=" + state +
                "; " + (isExpired() && (state != MyContextState.EXPIRED) ? "expired" : "") +
                accounts().size() + " accounts, " +
                (context == null ? "no context" : "context=" + context.getClass().getName());
    }

    @Override
    public MyContext newCreator(Context context, Object initializer) {
        return new MyContextImpl(null, context, initializer);
    }

    @Override
    public boolean initialized() {
        return state != MyContextState.EMPTY;
    }

    @Override
    public boolean isReady() {
        return state == MyContextState.READY && !DatabaseConverterController.isUpgrading();
    }

    @Override
    public MyContextState state() {
        return state;
    }
    
    @Override
    public Context context() {
        return context;
    }

    @Override
    public long preferencesChangeTime() {
        return preferencesChangeTime;
    }
    
    @Override
    public DatabaseHolder getMyDatabase() {
        return db;
    }

    @Override
    public SQLiteDatabase getDatabase() {
        if (db == null) {
            return null;
        }
        SQLiteDatabase sqLiteDatabase = null;
        try {
            sqLiteDatabase = this.db.getWritableDatabase();
        } catch (Exception e) {
            MyLog.e(this, "getDatabase", e);
        }
        return sqLiteDatabase;
    }

    /**
     * 2013-12-09 After getting the error "java.lang.IllegalStateException: attempt to re-open an already-closed object: SQLiteDatabase"
     * and reading Internet, I decided NOT to db.close here.
     */
    @Override
    public void release() {
        db = null;
    }

    @Override
    @NonNull
    public MyUsers users() {
        return users;
    }

    @Override
    @NonNull
    public MyAccounts accounts() {
        return accounts;
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    @Override
    public void setExpired() {
        MyLog.i(this, "setExpired");
        expired = true;
        state = MyContextState.EXPIRED;
    }

    @Override
    public PersistentOrigins origins() {
        return origins;
    }

    @NonNull
    @Override
    public PersistentTimelines timelines() {
        return timelines;
    }

    @Override
    public ConnectionState getConnectionState() {
        return UriUtils.getConnectionState(context);
    }

    @Override
    public boolean isInForeground() {
        if (!inForeground
                && !RelativeTime.moreSecondsAgoThan(inForegroundChangedAt,
                        CONSIDER_IN_BACKGROUND_AFTER_SECONDS)) {
            return true;
        }
        return inForeground;
    }

    @Override
    public void setInForeground(boolean inForeground) {
        setInForegroundStatic(inForeground);
    }
    
    /** To avoid "Write to static field" warning  
     *  On static members in interfaces: http://stackoverflow.com/questions/512877/why-cant-i-define-a-static-method-in-a-java-interface
     * */
    private static void setInForegroundStatic(boolean inForeground) {
        if (MyContextImpl.inForeground != inForeground) {
            inForegroundChangedAt = System.currentTimeMillis();
        }
        MyContextImpl.inForeground = inForeground;
    }

    @Override
    public Notifier getNotifier() {
        return notifier;
    }

    @Override
	public void notify(NotificationData data) {
        notifier.notifyAndroid(data);
	}

	@Override
	public void clearNotification(@NonNull Timeline timeline) {
        notifier.clear(timeline);
	}

    @Override
    public long getInstanceId() {
        return instanceId;
    }
}
