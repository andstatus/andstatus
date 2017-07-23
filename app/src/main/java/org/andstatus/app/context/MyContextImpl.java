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

package org.andstatus.app.context;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.support.annotation.NonNull;

import net.jcip.annotations.ThreadSafe;

import org.andstatus.app.ClassInApplicationPackage;
import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.data.AssertionData;
import org.andstatus.app.database.DatabaseConverterController;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.graphics.MyImageCache;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.origin.PersistentOrigins;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.timeline.PersistentTimelines;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.Locale;

/**
 * Contains global state of the application
 * The objects are effectively immutable
 * @author yvolk@yurivolkov.com
 */
@ThreadSafe
public final class MyContextImpl implements MyContext {

    final long instanceId = InstanceId.next();

    private volatile MyContextState mState = MyContextState.EMPTY;
    /**
     * Single context object for which we will request SharedPreferences
     */
    private volatile Context mContext = null;
    /**
     * Name of the object that initialized the class
     */
    private final String mInitializedBy;
    /**
     * When preferences, loaded into this class, were changed
     */
    private volatile long mPreferencesChangeTime = 0;
    private volatile DatabaseHolder mDb = null;
    private final PersistentAccounts mPersistentAccounts = PersistentAccounts.newEmpty(this);
    private final PersistentOrigins mPersistentOrigins = PersistentOrigins.newEmpty(this);
    private final PersistentTimelines persistentTimelines = PersistentTimelines.newEmpty(this);

    private volatile boolean mExpired = false;

    private final Locale mLocale = Locale.getDefault();
    
    private static volatile boolean mInForeground = false;
    private static volatile long mInForegroundChangedAt = 0;
    private static final long CONSIDER_IN_BACKGROUND_AFTER_SECONDS = 20;

    private MyContextImpl(Object initializerName) {
        mInitializedBy = MyLog.objToTag(initializerName);
    }

    @Override
    public MyContext newInitialized(Object initializer) {
        final String method = "newInitialized";
        MyContextImpl myContext = newNotInitialized(context(), initializer);
        if ( myContext.mContext == null) {
            // Nothing to do
        } else if (!Permissions.checkPermission(myContext.mContext,
                Permissions.PermissionType.GET_ACCOUNTS)) {
            myContext.mState = MyContextState.NO_PERMISSIONS;
        } else {
            MyLog.v(this, method + " Starting initialization of " + myContext.instanceId + " by " + myContext.mInitializedBy);
            myContext.initialize2();
        }
        MyLog.v(this, method + " " + myContext.toString());
        return myContext;
    }

    private void initialize2() {
        final String method = "initialize2";
        boolean createApplicationData = MyStorage.isApplicationDataCreated().not().toBoolean(false);
        if (createApplicationData) {
            MyLog.i(this, method + " Creating application data");
            MyPreferencesGroupsEnum.setDefaultValues();
            tryToSetExternalStorageOnDataCreation();
        }
        mPreferencesChangeTime = MyPreferences.getPreferencesChangeTime();
        initializeDatabase(createApplicationData);

        switch (mState) {
            case DATABASE_READY:
                mPersistentOrigins.initialize();
                if (MyContextHolder.isOnRestore()) {
                    mState = MyContextState.RESTORING;
                } else {
                    // Accounts are not restored yet
                    mPersistentAccounts.initialize();
                    persistentTimelines.initialize();
                    MyImageCache.initialize(context());
                    mState = MyContextState.READY;
                }
                break;
            default:
                break;
        }
    }

    private void initializeDatabase(boolean createApplicationData) {
        final String method = "initializeDatabase";
        DatabaseHolder newDb = new DatabaseHolder(mContext, createApplicationData);
        try {
            mState = newDb.checkState();
            if (state() == MyContextState.DATABASE_READY
                    && MyStorage.isApplicationDataCreated() != TriState.TRUE) {
                mState = MyContextState.ERROR;
            }
        } catch (SQLiteException e) {
            MyLog.e(this, method + " Error", e);
            mState = MyContextState.ERROR;
            newDb.close();
            mDb = null;
        }
        if (state() == MyContextState.DATABASE_READY) {
            mDb = newDb;
        }
    }

    private void tryToSetExternalStorageOnDataCreation() {
        boolean useExternalStorage = !Environment.isExternalStorageEmulated()
                && MyStorage.isWritableExternalStorageAvailable(null);
        MyLog.i(this, "External storage is " + (useExternalStorage ? "" : "not") + " used");
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, useExternalStorage);
    }

    @Override
    public String toString() {
        return  MyLog.getInstanceTag(this) + " by " + mInitializedBy + "; state=" + mState +
                "; " + (isExpired() && (mState != MyContextState.EXPIRED) ? "expired" : "") +
                persistentAccounts().size() + " accounts, " +
                (mContext == null ? "no context" : "context=" + mContext.getClass().getName());
    }

    @Override
    public MyContext newCreator(Context context, Object initializer) {
        return newNotInitialized(context, initializer);
    }

    private MyContextImpl newNotInitialized(Context context, Object initializer) {
        MyContextImpl newMyContext = newEmpty(initializer);
        if (context != null) {
            Context contextToUse = context.getApplicationContext();
        
            if ( contextToUse == null) {
                MyLog.w(this, "getApplicationContext is null, trying the context itself: " + context.getClass().getName());
                contextToUse = context;
            }
            // TODO: Maybe we need to determine if the context is compatible, using some Interface...
            // ...but we don't have any yet.
            if (!context.getClass().getName().contains(ClassInApplicationPackage.PACKAGE_NAME)) {
                MyLog.w(this, "Incompatible context: " + contextToUse.getClass().getName());
                contextToUse = null;
            }
            newMyContext.mContext = contextToUse;
        }
        return newMyContext;
    }
    
    public static MyContextImpl newEmpty(Object initializerName) {
        return new MyContextImpl(initializerName);
    }

    @Override
    public boolean initialized() {
        return mState != MyContextState.EMPTY;
    }

    @Override
    public boolean isReady() {
        return mState == MyContextState.READY && !DatabaseConverterController.isUpgrading();
    }

    @Override
    public MyContextState state() {
        return mState;
    }
    
    @Override
    public Context context() {
        return mContext;
    }

    @Override
    public String initializedBy() {
        return mInitializedBy;
    }
    
    @Override
    public long preferencesChangeTime() {
        return mPreferencesChangeTime;
    }
    
    @Override
    public DatabaseHolder getMyDatabase() {
        return mDb;
    }

    @Override
    public SQLiteDatabase getDatabase() {
        if (mDb == null) {
            return null;
        }
        SQLiteDatabase db = null;
        try {
            db = mDb.getWritableDatabase();
        } catch (Exception e) {
            MyLog.e(this, "getDatabase", e);
        }
        return db;
    }

    @Override
    /**
     * 2013-12-09 After getting the error "java.lang.IllegalStateException: attempt to re-open an already-closed object: SQLiteDatabase"
     * and reading Internet, I decided NOT to db.close here.
     */
    public void release() {
        // Nothing to do here
    }

    @Override
    public PersistentAccounts persistentAccounts() {
        return mPersistentAccounts;
    }

    @Override
    public boolean isTestRun() {
        return false;
    }

    @Override
    public void put(AssertionData data) {
        // Noop for this implementation
    }

    @Override
    public boolean isExpired() {
        return mExpired;
    }

    @Override
    public void setExpired() {
        MyLog.i(this, "setExpired");
        mExpired = true;
        mState = MyContextState.EXPIRED;
    }

    @Override
    public Locale getLocale() {
        return mLocale;
    }

    @Override
    public PersistentOrigins persistentOrigins() {
        return mPersistentOrigins;
    }

    @NonNull
    @Override
    public PersistentTimelines persistentTimelines() {
        return persistentTimelines;
    }

    @Override
    public HttpConnection getHttpConnectionMock() {
        return null;
    }

    /**
     * Based on http://stackoverflow.com/questions/1560788/how-to-check-internet-access-on-android-inetaddress-never-timeouts
     */
    @Override
    public ConnectionState getConnectionState() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return ConnectionState.UNKNOWN;
        }
        ConnectionState state = ConnectionState.OFFLINE;
        NetworkInfo networkInfoOnline = connectivityManager.getActiveNetworkInfo();
        if (networkInfoOnline == null) {
            return state;
        }
        if (networkInfoOnline.isAvailable() && networkInfoOnline.isConnected()) {
            state = ConnectionState.ONLINE;
        } else {
            return state;
        }
        NetworkInfo networkInfoWiFi = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfoWiFi == null) {
            return state;
        }
        if (networkInfoWiFi.isAvailable() && networkInfoWiFi.isConnected()) {
            state = ConnectionState.WIFI;
        }
        return state;
    }

    @Override
    public boolean isInForeground() {
        if (!mInForeground
                && !RelativeTime.moreSecondsAgoThan(mInForegroundChangedAt,
                        CONSIDER_IN_BACKGROUND_AFTER_SECONDS)) {
            return true;
        }
        return mInForeground;
    }

    @Override
    public void setInForeground(boolean inForeground) {
        setInForegroundStatic(inForeground);
    }
    
    /** To avoid "Write to static field" warning  
     *  On static members in interfaces: http://stackoverflow.com/questions/512877/why-cant-i-define-a-static-method-in-a-java-interface
     * */
    private static void setInForegroundStatic(boolean inForeground) {
        if (mInForeground != inForeground) {
            mInForegroundChangedAt = System.currentTimeMillis();
        }
        mInForeground = inForeground;
    }

	@Override
	public void notify(TimelineType id, Notification notification) {
        NotificationManager nM = (NotificationManager) context().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        nM.notify(MyLog.APPTAG, id.ordinal(), notification);
	}

	@Override
	public void clearNotification(TimelineType id) {
        NotificationManager nM = (NotificationManager) context().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        nM.cancel(MyLog.APPTAG, id.ordinal());
	}

    @Override
    public long getInstanceId() {
        return instanceId;
    }
}
