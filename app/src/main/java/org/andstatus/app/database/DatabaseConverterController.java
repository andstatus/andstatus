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

package org.andstatus.app.database;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.R;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDataChecker;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.TimelineSaver;
import org.andstatus.app.util.MyLog;

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
    private static Activity upgradeRequestor = null;
    
    static final long SECONDS_BEFORE_UPGRADE_TRIGGERED = 5L;
    static final int UPGRADE_LENGTH_SECONDS_MAX = 90;

    public static void attemptToTriggerDatabaseUpgrade(Activity upgradeRequestorIn) {
        String requestorName = MyLog.objTagToString(upgradeRequestorIn);
        boolean skip = false;
        if (isUpgrading()) {
            MyLog.v(TAG, "Attempt to trigger database upgrade by " + requestorName 
                    + ": already upgrading");
            skip = true;
        }
        if (!skip && !MyContextHolder.get().initialized()) {
            MyLog.v(TAG, "Attempt to trigger database upgrade by " + requestorName 
                    + ": not initialized yet");
            skip = true;
        }
        if (!skip && acquireUpgradeLock(requestorName)) {
            new AsyncTaskLauncher<Activity>().execute(TAG, true, new AsyncUpgrade(), upgradeRequestorIn);
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

    private static class AsyncUpgrade extends MyAsyncTask<Activity, Void, Void> {
        Activity locUpgradeRequestor = null;
        ProgressLogger.ProgressCallback progressCallback = ProgressLogger.getEmptyCallback();

        public AsyncUpgrade() {
            super(PoolEnum.LONG_UI);
        }

        @Override
        protected Void doInBackground2(Activity... activity) {
            boolean success = false;
            locUpgradeRequestor = activity[0];
            if (ProgressLogger.ProgressCallback.class.isAssignableFrom(locUpgradeRequestor.getClass())) {
                progressCallback = (ProgressLogger.ProgressCallback) locUpgradeRequestor;
            }
            try {
                progressCallback.onProgressMessage(locUpgradeRequestor.getText(R.string.label_upgrading));
                success = doUpgrade();
            } finally {
                progressCallback.onComplete(success);
            }
            return null;
        }

        private boolean doUpgrade() {
            boolean success = false;
            boolean locUpgradeStarted = false;
            try {
                synchronized(UPGRADE_LOCK) {
                    upgradeRequestor = locUpgradeRequestor;
                }
                MyLog.v(TAG, "Upgrade triggered by " + MyLog.objTagToString(locUpgradeRequestor));
                MyServiceManager.setServiceUnavailable();
                MyContextHolder.release();
                // Upgrade will occur inside this call synchronously
                MyContextHolder.initializeDuringUpgrade(locUpgradeRequestor, locUpgradeRequestor);
                synchronized(UPGRADE_LOCK) {
                    shouldTriggerDatabaseUpgrade = false;
                }
            } catch (Exception e) {
                MyLog.i(TAG, "Failed to trigger database upgrade, will try later", e);
            } finally {
                synchronized(UPGRADE_LOCK) {
                    success = upgradeEndedSuccessfully;
                    upgradeRequestor = null;
                    locUpgradeStarted = upgradeStarted;
                    upgradeStarted = false;
                    upgradeEndTime = 0L;
                }
            }
            if (!locUpgradeStarted) {
                MyLog.v(TAG, "Upgrade didn't start");
            }
            if (success) {
                MyLog.v(TAG, "success " + MyContextHolder.get().state());
                onUpgradeSucceeded();
            }
            return success;
        }

        private void onUpgradeSucceeded() {
            MyServiceManager.setServiceUnavailable();
            if (!MyContextHolder.get().isReady()) {
                MyContextHolder.release();
                MyContextHolder.initialize(locUpgradeRequestor, locUpgradeRequestor);
            }
            if (MyContextHolder.get().isReady()) {
                MyServiceManager.stopService();
                new TimelineSaver(MyContextHolder.get()).setAddDefaults(true).executeNotOnUiThread();
                new MyDataChecker(MyContextHolder.get(), new ProgressLogger(progressCallback)).fixData();
            }
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
    
    void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)  {
        if (!shouldTriggerDatabaseUpgrade) {
            MyLog.v(this,"onUpgrade - Trigger not set yet");
            throw new IllegalStateException("onUpgrade - Trigger not set yet");
        }
        synchronized (UPGRADE_LOCK) {
            shouldTriggerDatabaseUpgrade = false;
            stillUpgrading();
        }
        MyContextHolder.get().setInForeground(true);
        boolean success = new DatabaseConverter().execute(new UpgradeParams(upgradeRequestor, db,
                oldVersion, newVersion));
        synchronized(UPGRADE_LOCK) {
            upgradeEnded = true;
            upgradeEndedSuccessfully = success;
        }
        if (!success) {
            throw new IllegalStateException("Upgrade failed");
        }
    }

    static class UpgradeParams {
        Activity upgradeRequestor;
        SQLiteDatabase db;
        int oldVersion;
        int newVersion;

        UpgradeParams(Activity upgradeRequestor, SQLiteDatabase db, int oldVersion, int newVersion) {
            this.upgradeRequestor = upgradeRequestor;
            this.db = db;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }
    }
}
