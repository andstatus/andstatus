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
 * distributed under MyDatabaseConverterExecutor License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.data.converter;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.backup.DefaultProgressListener;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.data.checker.DataChecker;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class DatabaseConverterController {
    private static final String TAG = DatabaseConverterController.class.getSimpleName();

    private static final Object UPGRADE_LOCK = new Object();
    @GuardedBy("upgradeLock")
    private static volatile boolean shouldTriggerDatabaseUpgrade = false;
    /**
     * Semaphore enabling uninterrupted system upgrade
     */
    @GuardedBy("upgradeLock")
    private static long upgradeEndTime = 0L;
    @GuardedBy("upgradeLock")
    private static boolean upgradeStarted = false;
    @GuardedBy("upgradeLock")
    private static boolean upgradeEnded = false;
    @GuardedBy("upgradeLock")
    private static boolean upgradeEndedSuccessfully = false;
    @GuardedBy("upgradeLock")
    private static ProgressLogger mProgressLogger = null;
    
    static final long SECONDS_BEFORE_UPGRADE_TRIGGERED = 5L;
    static final int UPGRADE_LENGTH_SECONDS_MAX = 90;

    public static void attemptToTriggerDatabaseUpgrade(@NonNull Activity upgradeRequestorIn) {
        String requestorName = MyStringBuilder.objToTag(upgradeRequestorIn);
        boolean skip = false;
        if (isUpgrading()) {
            MyLog.v(TAG, "Attempt to trigger database upgrade by " + requestorName 
                    + ": already upgrading");
            skip = true;
        }
        if (!skip && !myContextHolder.getNow().initialized()) {
            MyLog.v(TAG, "Attempt to trigger database upgrade by " + requestorName 
                    + ": not initialized yet");
            skip = true;
        }
        if (!skip && acquireUpgradeLock(requestorName)) {
            final AsyncUpgrade asyncUpgrade = new AsyncUpgrade(upgradeRequestorIn, myContextHolder.isOnRestore());
            if (myContextHolder.isOnRestore()) {
                asyncUpgrade.syncUpgrade();
            } else {
                AsyncTaskLauncher.execute(TAG, asyncUpgrade);
            }
        }
    }

    private static boolean acquireUpgradeLock(String requestorName) {
        boolean skip = false;
        synchronized(UPGRADE_LOCK) {
            if (isUpgrading()) {
                MyLog.v(TAG, "Attempt to trigger database upgrade by " + requestorName 
                        + ": already upgrading");
                skip = true;
            }
            if (!skip && upgradeEnded) {
                MyLog.v(TAG, "Attempt to trigger database upgrade by " + requestorName 
                        + ": already completed " + (upgradeEndedSuccessfully ? " successfully" : "(failed)"));
                skip = true;
                if (!upgradeEndedSuccessfully) {
                    upgradeEnded = false;
                }
            }
            if (!skip) {
                MyLog.v(TAG, "Upgrade lock acquired for " + requestorName);
                long startTime = java.lang.System.currentTimeMillis();
                upgradeEndTime = startTime + java.util.concurrent.TimeUnit.SECONDS.toMillis(SECONDS_BEFORE_UPGRADE_TRIGGERED);
                shouldTriggerDatabaseUpgrade = true;            
            }
        }
        return !skip;
    }

    private static class AsyncUpgrade extends MyAsyncTask<Void, Void, Void> {
        @NonNull
        final Activity upgradeRequestor;
        final boolean isRestoring;
        final ProgressLogger progressLogger;

        AsyncUpgrade(@NonNull Activity upgradeRequestor, boolean isRestoring) {
            super(PoolEnum.LONG_UI);
            this.upgradeRequestor = upgradeRequestor;
            this.isRestoring = isRestoring;
            if (upgradeRequestor instanceof MyActivity) {
                ProgressLogger.ProgressListener progressListener =
                        new DefaultProgressListener((MyActivity) upgradeRequestor, R.string.label_upgrading, "ConvertDatabase");
                progressLogger = new ProgressLogger(progressListener);
            } else {
                progressLogger = ProgressLogger.getEmpty("ConvertDatabase");
            }
        }

        @Override
        protected Void doInBackground2(Void aVoid) {
            syncUpgrade();
            return null;
        }

        private void syncUpgrade() {
            boolean success = false;
            try {
                progressLogger.logProgress(upgradeRequestor.getText(R.string.label_upgrading));
                success = doUpgrade();
            } finally {
                progressLogger.onComplete(success);
            }
        }

        private boolean doUpgrade() {
            boolean success = false;
            boolean locUpgradeStarted = false;
            try {
                synchronized(UPGRADE_LOCK) {
                    mProgressLogger = progressLogger;
                }
                MyLog.i(TAG, "Upgrade triggered by " + MyStringBuilder.objToTag(upgradeRequestor));
                MyServiceManager.setServiceUnavailable();
                myContextHolder.release(() -> "doUpgrade");
                // Upgrade will occur inside this call synchronously
                // TODO: Add completion stage instead of blocking...
                myContextHolder.initializeDuringUpgrade(upgradeRequestor).getBlocking();
                synchronized(UPGRADE_LOCK) {
                    shouldTriggerDatabaseUpgrade = false;
                }
            } catch (Exception e) {
                MyLog.i(TAG, "Failed to trigger database upgrade, will try later", e);
            } finally {
                synchronized(UPGRADE_LOCK) {
                    success = upgradeEndedSuccessfully;
                    mProgressLogger = null;
                    locUpgradeStarted = upgradeStarted;
                    upgradeStarted = false;
                    upgradeEndTime = 0L;
                }
            }
            if (!locUpgradeStarted) {
                MyLog.v(TAG, "Upgrade didn't start");
            }
            if (success) {
                MyLog.i(TAG, "success " + myContextHolder.getNow().state());
                onUpgradeSucceeded();
            }
            return success;
        }

        private void onUpgradeSucceeded() {
            MyServiceManager.setServiceUnavailable();
            if (!myContextHolder.getNow().isReady()) {
                myContextHolder.release(() -> "onUpgradeSucceeded1");
                myContextHolder.initialize(upgradeRequestor).getBlocking();
            }
            MyServiceManager.setServiceUnavailable();
            MyServiceManager.stopService();
            if (isRestoring) return;

            DataChecker.fixData(progressLogger, false, false);
            myContextHolder.release(() -> "onUpgradeSucceeded2");
            myContextHolder.initialize(upgradeRequestor).getBlocking();
            MyServiceManager.setServiceAvailable();
        }
    }
    
    public static void stillUpgrading() {
        boolean wasStarted;
        synchronized(UPGRADE_LOCK) {
            wasStarted = upgradeStarted;
            upgradeStarted = true;
            upgradeEndTime = java.lang.System.currentTimeMillis() + java.util.concurrent.TimeUnit.SECONDS.toMillis(UPGRADE_LENGTH_SECONDS_MAX);
        }
        MyLog.w(TAG, (wasStarted ? "Still upgrading" : "Upgrade started") + ". Wait " + UPGRADE_LENGTH_SECONDS_MAX + " seconds");
    }
    
    public static boolean isUpgradeError() {
        synchronized(UPGRADE_LOCK) {
            if (upgradeEnded && !upgradeEndedSuccessfully) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUpgrading() {
        synchronized(UPGRADE_LOCK) {
            if (upgradeEndTime == 0 ) {
                return false;
            }
            long currentTime = java.lang.System.currentTimeMillis();
            if (currentTime > upgradeEndTime) {
                MyLog.v(TAG,"Upgrade end time came");
                upgradeEndTime = 0L;
                return false;
            }
        }
        return true;
    }
    
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)  {
        if (!shouldTriggerDatabaseUpgrade) {
            MyLog.v(this,"onUpgrade - Trigger not set yet");
            throw new IllegalStateException("onUpgrade - Trigger not set yet");
        }
        synchronized (UPGRADE_LOCK) {
            shouldTriggerDatabaseUpgrade = false;
            stillUpgrading();
        }
        myContextHolder.getNow().setInForeground(true);
        final DatabaseConverter databaseConverter = new DatabaseConverter();
        boolean success = databaseConverter.execute(new UpgradeParams(mProgressLogger, db, oldVersion, newVersion));
        synchronized(UPGRADE_LOCK) {
            upgradeEnded = true;
            upgradeEndedSuccessfully = success;
        }
        if (!success) {
            throw new ApplicationUpgradeException(databaseConverter.converterError);
        }
    }

    static class UpgradeParams {
        ProgressLogger progressLogger;
        SQLiteDatabase db;
        int oldVersion;
        int newVersion;

        UpgradeParams(ProgressLogger progressLogger, SQLiteDatabase db, int oldVersion, int newVersion) {
            this.progressLogger = progressLogger;
            this.db = db;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }
    }
}
