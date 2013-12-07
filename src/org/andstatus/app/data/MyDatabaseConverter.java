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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.data;

import android.database.sqlite.SQLiteDatabase;

import net.jcip.annotations.GuardedBy;

import org.andstatus.app.MyContext;
import org.andstatus.app.MyContextHolder;
import org.andstatus.app.account.MyAccountConverter;
import org.andstatus.app.util.MyLog;

public class MyDatabaseConverter {
    private static final String TAG = MyDatabaseConverter.class.getSimpleName();

    private final static Object upgradeLock = new Object();
    @GuardedBy("upgradeLock")
    private static volatile boolean shouldTriggerDatabaseUpgrade = false;
    /**
     * Semaphore enabling uninterrupted system upgrade
     */
    @GuardedBy("upgradeLock")
    private static Long upgradeEndTime = 0L;
    @GuardedBy("upgradeLock")
    private static boolean upgradeStarted = false;
    @GuardedBy("upgradeLock")
    private static boolean upgradeSuccessfullyCompleted = false;
    
    final static Long SECONDS_BEFORE_UPGRADE_TRIGGERED = 5L;
    final static long SECONDS_FOR_UPGRADE = 30L;
    final static Long SECONDS_AFTER_UPGRADE = 5L;

    public static void triggerDatabaseUpgrade(Object requester) {
        String requesterName = MyLog.objTagToString(requester);
        if (isUpgrading()) {
            MyLog.v(TAG, "Attempt to trigger database upgrade by " + requesterName 
                    + ": already upgrading");
            return;
        }
        MyContext myContext = MyContextHolder.get();
        long currentTime = java.lang.System.currentTimeMillis();
        if (!myContext.initialized()) {
            MyLog.v(TAG, "Attempt to trigger database upgrade by " + requesterName 
                    + ": not initialized yet");
            return;
        }
        synchronized(upgradeLock) {
            if (isUpgrading()) {
                MyLog.v(TAG, "Attempt to trigger database upgrade by " + requesterName 
                        + ": already upgrading");
                return;
            }
            if (upgradeSuccessfullyCompleted) {
                MyLog.v(TAG, "Attempt to trigger database upgrade by " + requesterName 
                        + ": already completed successfully");
                return;
            }
            upgradeEndTime = currentTime + java.util.concurrent.TimeUnit.SECONDS.toMillis(SECONDS_BEFORE_UPGRADE_TRIGGERED);
            shouldTriggerDatabaseUpgrade = true;            
        }
        try {
            MyLog.v(TAG, "Upgrade triggered by " + requesterName);
            MyContextHolder.release();
            MyContextHolder.initialize(myContext.context(), TAG);
            synchronized(upgradeLock) {
                shouldTriggerDatabaseUpgrade = false;
                upgradeSuccessfullyCompleted = true;
                if (upgradeStarted) {
                    upgradeStarted = false;
                    MyLog.v(TAG, "Upgraded completed successfully");
                } else {
                    MyLog.v(TAG, "No upgrade was required");
                }
            }
        } catch (Exception e) {
            MyLog.i(TAG, "Failed to trigger database upgrade, will try later", e);
            
        } finally {
            currentTime = java.lang.System.currentTimeMillis();
            synchronized(upgradeLock) {
                if (upgradeStarted) {
                    upgradeEndTime = currentTime + java.util.concurrent.TimeUnit.SECONDS.toMillis(SECONDS_AFTER_UPGRADE);
                    MyLog.w(TAG, "Upgrade ended, waiting " + SECONDS_AFTER_UPGRADE + " more seconds");
                } else {
                    upgradeEndTime = 0L;
                }
            }
        }
    }
    
    public static void stillUpgrading() {
        synchronized(upgradeLock) {
            upgradeStarted = true;
            upgradeEndTime = java.lang.System.currentTimeMillis() + java.util.concurrent.TimeUnit.SECONDS.toMillis(SECONDS_FOR_UPGRADE);
        }
        MyLog.w(TAG, "Still upgrading. Wait " + SECONDS_FOR_UPGRADE + " more seconds");
    }
    
    public static boolean isUpgrading() {
        synchronized(upgradeLock) {
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
        synchronized (upgradeLock) {
            shouldTriggerDatabaseUpgrade = false;
        }
        int currentVersion = oldVersion;
        stillUpgrading();
        MyLog.i(this, "Upgrading database from version " + oldVersion + " to version " + newVersion);
        if (oldVersion < 9) {
            throw new IllegalArgumentException("Upgrade from this database version is not supported. Please reinstall the application");
        } 
        if (currentVersion == 9) {
            currentVersion = convert9to10(db, currentVersion);
        }
        if (currentVersion == 10) {
            currentVersion = convert10to11(db, currentVersion);
        }
        if (currentVersion == 11) {
            currentVersion = convert11to12(db, currentVersion);
        }
        if (currentVersion == 12) {
            currentVersion = convert12to13(db, currentVersion);
        }
        if ( currentVersion == newVersion) {
            MyLog.i(this, "Successfully upgraded database from version " + oldVersion + " to version "
                    + newVersion + ".");
        } else {
            MyLog.e(this, "Error upgrading database from version " + oldVersion + " to version "
                    + newVersion + ". Current database version=" + currentVersion);
            throw new IllegalStateException("Database upgrade failed. Current database version=" + currentVersion);
        }
    }

    /**
     * @return new db version, the same as old in a case of a failure
     */
    private int convert9to10(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 10;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );
            String[] columns = {"home_timeline_msg_id", "home_timeline_date", 
                    "favorites_timeline_msg_id", "favorites_timeline_date",
                    "direct_timeline_msg_id", "direct_timeline_date", 
                    "mentions_timeline_msg_id", "mentions_timeline_date", 
                    "user_timeline_msg_id", "user_timeline_date"};
            for ( String column: columns ) {
                sql = "ALTER TABLE user ADD COLUMN " + column + " INTEGER DEFAULT 0 NOT NULL";
                db.execSQL(sql);
            }
            
            sql = "ALTER TABLE msgofuser ADD COLUMN reblog_oid STRING";
            db.execSQL(sql);
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }

    /**
     * @return new db version, the same as old in a case of a failure
     */
    private int convert10to11(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 11;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            String[] columns = {"following_user_date",
                    "user_msg_id", "user_msg_date"};
            for ( String column: columns ) {
                sql = "ALTER TABLE user ADD COLUMN " + column + " INTEGER DEFAULT 0 NOT NULL";
                db.execSQL(sql);
            }
            
            sql = "CREATE TABLE " + "followinguser" + " (" 
                    + "user_id" + " INTEGER NOT NULL," 
                    + "following_user_id" + " INTEGER NOT NULL," 
                    + "user_followed" + " BOOLEAN DEFAULT 1 NOT NULL," 
                    + " CONSTRAINT pk_followinguser PRIMARY KEY (" + "user_id" + " ASC, " + "following_user_id" + " ASC)"
                    + ");";
            db.execSQL(sql);
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }

    /**
     * @return new db version, the same as old in a case of a failure
     */
    private int convert11to12(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 12;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            sql = "ALTER TABLE msg ADD COLUMN url TEXT";
            db.execSQL(sql);
            
            sql = "ALTER TABLE msgofuser ADD COLUMN reblogged INTEGER DEFAULT 0 NOT NULL";
            db.execSQL(sql);
            sql = "UPDATE msgofuser SET reblogged = retweeted";
            db.execSQL(sql);
            // DROP COLUMN is not supported so we have to recreate table
            sql = "ALTER TABLE msgofuser RENAME TO msgofuser_old";
            db.execSQL(sql);
            sql = "CREATE TABLE msgofuser (user_id INTEGER NOT NULL,msg_id INTEGER NOT NULL,subscribed BOOLEAN DEFAULT 0 NOT NULL,"
                    + "favorited BOOLEAN DEFAULT 0 NOT NULL,reblogged BOOLEAN DEFAULT 0 NOT NULL,reblog_oid TEXT,"
                    + "mentioned BOOLEAN DEFAULT 0 NOT NULL,replied BOOLEAN DEFAULT 0 NOT NULL,directed BOOLEAN DEFAULT 0 NOT NULL,"
                    + " CONSTRAINT pk_msgofuser PRIMARY KEY (user_id ASC, msg_id ASC))";
            db.execSQL(sql);
            sql = "INSERT INTO msgofuser (user_id, msg_id, subscribed, favorited, reblogged, reblog_oid, mentioned, replied, directed)"
                    + " SELECT user_id, msg_id, subscribed, favorited, reblogged, reblog_oid, mentioned, replied, directed FROM msgofuser_old";
            db.execSQL(sql);
            sql = "DROP TABLE msgofuser_old";
            db.execSQL(sql);
            
            sql = "UPDATE user SET username = username || '@identi.ca'"
                    + " WHERE origin_id=2";
            db.execSQL(sql);
            
            sql = "UPDATE user SET user_oid = 'acct:' || username"
                    + " WHERE origin_id=2";
            db.execSQL(sql);
            
            sql = "UPDATE msg SET msg_oid='http://identi.ca/notice/' || msg_oid"
                    + " WHERE origin_id=2";
            db.execSQL(sql);
            
            String[] columnsOld = {
                    "home_timeline_msg_id", "favorites_timeline_msg_id", 
                    "direct_timeline_msg_id", "mentions_timeline_msg_id", "user_timeline_msg_id"
            };
            String[] columnsNew = {
                    "home_timeline_position", "favorites_timeline_position", 
                    "direct_timeline_position", "mentions_timeline_position", "user_timeline_position"
            };
            String[] columnsItemDate = {
                    "home_timeline_item_date", "favorites_timeline_item_date", 
                    "direct_timeline_item_date", "mentions_timeline_item_date", "user_timeline_item_date"
            };
            for (int index=0; index < columnsNew.length; index++ ) {
                sql = "UPDATE user SET " + columnsOld[index] + "=''" + " WHERE origin_id=2";
                db.execSQL(sql);
                sql = "ALTER TABLE user ADD COLUMN " + columnsNew[index] + " TEXT DEFAULT '' NOT NULL";
                db.execSQL(sql);
                sql = "UPDATE user SET " + columnsNew[index] + "=" + columnsOld[index];
                db.execSQL(sql);
                sql = "ALTER TABLE user ADD COLUMN " + columnsItemDate[index] + " INTEGER DEFAULT 0 NOT NULL";
                db.execSQL(sql);
            }

            sql = "ALTER TABLE user RENAME TO user_old";
            db.execSQL(sql);
            sql="CREATE TABLE user (_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "origin_id INTEGER DEFAULT 1 NOT NULL,user_oid TEXT,username TEXT NOT NULL,"
                    + "real_name TEXT,avatar_url TEXT,avatar_blob BLOB,user_description TEXT,"
                    + "homepage TEXT,url TEXT,user_created_date INTEGER,user_ins_date INTEGER NOT NULL,"
                    + "home_timeline_position TEXT DEFAULT '' NOT NULL,home_timeline_item_date INTEGER DEFAULT 0 NOT NULL,"
                    + "home_timeline_date INTEGER DEFAULT 0 NOT NULL,"
                    + "favorites_timeline_position TEXT DEFAULT '' NOT NULL,favorites_timeline_item_date INTEGER DEFAULT 0 NOT NULL,"
                    + "favorites_timeline_date INTEGER DEFAULT 0 NOT NULL,"
                    + "direct_timeline_position TEXT DEFAULT '' NOT NULL,direct_timeline_item_date INTEGER DEFAULT 0 NOT NULL,"
                    + "direct_timeline_date INTEGER DEFAULT 0 NOT NULL,"
                    + "mentions_timeline_position TEXT DEFAULT '' NOT NULL,mentions_timeline_item_date INTEGER DEFAULT 0 NOT NULL,"
                    + "mentions_timeline_date INTEGER DEFAULT 0 NOT NULL,"
                    + "user_timeline_position TEXT DEFAULT '' NOT NULL,user_timeline_item_date INTEGER DEFAULT 0 NOT NULL,"
                    + "user_timeline_date INTEGER DEFAULT 0 NOT NULL,"
                    + "following_user_date INTEGER DEFAULT 0 NOT NULL,user_msg_id INTEGER DEFAULT 0 NOT NULL,"
                    + "user_msg_date INTEGER DEFAULT 0 NOT NULL)";
            db.execSQL(sql);
            sql = "INSERT INTO user (_id, origin_id, user_oid, username, real_name, avatar_url, avatar_blob, user_description,"
                    + "homepage, user_created_date, user_ins_date,"
                    + "home_timeline_position, home_timeline_item_date, home_timeline_date,"
                    + "favorites_timeline_position, favorites_timeline_item_date, favorites_timeline_date,"
                    + "direct_timeline_position, direct_timeline_item_date, direct_timeline_date,"
                    + "mentions_timeline_position, mentions_timeline_item_date, mentions_timeline_date,"
                    + "user_timeline_position, user_timeline_item_date, user_timeline_date,"
                    + "following_user_date, user_msg_id, user_msg_date)"
                    + " SELECT _id, origin_id, user_oid, username, real_name, avatar_url, avatar_blob, user_description,"
                    + "homepage, user_created_date, user_ins_date,"
                    + "home_timeline_position, home_timeline_item_date, home_timeline_date,"
                    + "favorites_timeline_position, favorites_timeline_item_date, favorites_timeline_date,"
                    + "direct_timeline_position, direct_timeline_item_date, direct_timeline_date,"
                    + "mentions_timeline_position, mentions_timeline_item_date, mentions_timeline_date,"
                    + "user_timeline_position, user_timeline_item_date, user_timeline_date,"
                    + "following_user_date, user_msg_id, user_msg_date FROM user_old";
            db.execSQL(sql);
            sql = "DROP TABLE user_old";
            db.execSQL(sql);
                        
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
            ok = ( MyAccountConverter.convert11to12(db, oldVersion) == versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }
 
    private int convert12to13(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 13;
        boolean ok = false;
        String sql = "";
        try {
            MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo );

            MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_SHOW_AVATARS, false).commit();
            
            db.execSQL("CREATE TABLE avatar (_id INTEGER PRIMARY KEY AUTOINCREMENT," 
                    + "user_id INTEGER NOT NULL," 
                    + "avatar_valid_from INTEGER NOT NULL," 
                    + "avatar_url TEXT NOT NULL," 
                    + "avatar_file_name TEXT," 
                    + "avatar_status INTEGER NOT NULL DEFAULT 0," 
                    + "avatar_loaded_date INTEGER)");

            db.execSQL("CREATE INDEX idx_avatar_user ON avatar (" 
                    + "user_id, "
                    + "avatar_status"
                    + ")");
                        
            ok = true;
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion 
                    + " to version " + versionTo
                    + " SQL='" + sql +"'");
        }
        return ok ? versionTo : oldVersion;
    }
    
}
