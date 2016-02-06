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

package org.andstatus.app.data;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.util.MyLog;

public class MyDatabaseConverterController {
    private static final String TAG = MyDatabaseConverterController.class.getSimpleName();

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
    static final long SECONDS_FOR_UPGRADE = 30L;

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
        if (!skip && aquireUpgradeLock(requestorName)) {
            doUpgrade(upgradeRequestorIn);
        }
    }

    private static boolean aquireUpgradeLock(String requestorName) {
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

    private static void doUpgrade(Activity upgradeRequestor) {
        new AsyncTaskLauncher<Activity>().execute(TAG, new AsyncUpgrade(), true, upgradeRequestor);
    }
    
    private static class AsyncUpgrade extends MyAsyncTask<Activity, Void, Void> {

        @Override
        protected Void doInBackground2(Activity... activity) {
            boolean success = false;
            try {
                synchronized(UPGRADE_LOCK) {
                    upgradeRequestor = activity[0];
                }
                MyLog.v(TAG, "Upgrade triggered by " + MyLog.objTagToString(activity[0]));
                MyContextHolder.release();
                // Upgrade will occur inside this call synchronously
                MyContextHolder.initializeDuringUpgrade(activity[0], activity[0]);
                synchronized(UPGRADE_LOCK) {
                    shouldTriggerDatabaseUpgrade = false;
                }
            } catch (Exception e) {
                MyLog.i(TAG, "Failed to trigger database upgrade, will try later", e);
            } finally {
                synchronized(UPGRADE_LOCK) {
                    success = upgradeEndedSuccessfully;
                    upgradeRequestor = null;
                    if (upgradeStarted) {
                        upgradeStarted = false;
                    } else {
                        MyLog.v(TAG, "Upgrade didn't start");                    
                    }
                    upgradeEndTime = 0L;
                }
            }
            if (success) {
                MyPreferences.onPreferencesChanged();
                if (!MyContextHolder.get().isReady()) {
                    MyContextHolder.initialize(activity[0], activity[0]);
                }
            }
            return null;
        }
        
    }
    
    public static void stillUpgrading() {
        boolean wasStarted;
        synchronized(UPGRADE_LOCK) {
            wasStarted = upgradeStarted;
            upgradeStarted = true;
            upgradeEndTime = java.lang.System.currentTimeMillis() + java.util.concurrent.TimeUnit.SECONDS.toMillis(SECONDS_FOR_UPGRADE);
        }
        MyLog.w(TAG, (wasStarted ? "Still upgrading" : "Upgrade started") + ". Wait " + SECONDS_FOR_UPGRADE + " seconds");
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
        boolean success = new MyDatabaseConverter().execute(new UpgradeParams(upgradeRequestor, db,
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
