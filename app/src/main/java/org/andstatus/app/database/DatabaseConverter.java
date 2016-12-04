/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.database;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import org.andstatus.app.account.MyAccountConverter;
import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyStorage;
import org.andstatus.app.data.ApplicationUpgradeException;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;

import java.io.File;

class DatabaseConverter {
    public static final int PARTIAL_INDEX_SUPPORTED = Build.VERSION_CODES.LOLLIPOP;
    long startTime = java.lang.System.currentTimeMillis();
    private Activity activity;
    ProgressLogger.ProgressCallback progressCallback = ProgressLogger.getEmptyCallback();

    protected boolean execute(DatabaseConverterController.UpgradeParams params) {
        boolean success = false;
        activity = params.upgradeRequestor;
        if (ProgressLogger.ProgressCallback.class.isAssignableFrom(params.upgradeRequestor.getClass())) {
            progressCallback = (ProgressLogger.ProgressCallback) params.upgradeRequestor;
        }
        String msgLog = "";
        long endTime = 0;
        try {
            convertAll(params.db, params.oldVersion, params.newVersion);
            success = true;
            endTime = java.lang.System.currentTimeMillis();
        } catch (ApplicationUpgradeException e) {
            endTime = java.lang.System.currentTimeMillis();
            msgLog = e.getMessage();
            progressCallback.onProgressMessage(msgLog);
            MyLog.ignored(this, e);
            DbUtils.waitMs("execute, on ApplicationUpgradeException", 30000);
        } finally {
            DbUtils.waitMs("execute finally", 2000);
            if (success) {
                msgLog = "Upgrade successfully completed in "
                        + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                        + " seconds";
                MyLog.i(this, msgLog);
            } else {
                msgLog = "Upgrade failed in "
                        + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                        + " seconds";
                MyLog.e(this, msgLog);
            }
            DbUtils.waitMs("execute finally 2", 500);
            if (MyContextHolder.get().isTestRun()) {
                activity.finish();
            }
        }
        return success;
    }

    private void convertAll(SQLiteDatabase db, int oldVersion, int newVersion) throws ApplicationUpgradeException {
        int currentVersion = oldVersion;
        MyLog.i(this, "Upgrading database from version " + oldVersion + " to version " + newVersion);
        boolean converterNotFound = false;
        String lastError = "?";
        OneStep oneStep;
        do {
            oneStep = null;
            try {
                int prevVersion = currentVersion;
                Class clazz = Class.forName(this.getClass().getName() + "$Convert" + Integer.toString(currentVersion));
                oneStep = (OneStep) clazz.newInstance();
                currentVersion = oneStep.execute(db, currentVersion);
                if (currentVersion == prevVersion) {
                    lastError = oneStep.getLastError();
                    MyLog.e(this, "Stuck at version " + prevVersion + "\n"
                            + "Error: " + lastError);
                    oneStep = null;
                }
            } catch (ClassNotFoundException e) {
                converterNotFound = true;
                String msgLog = "No converter for version " + currentVersion;
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog, e);
                } else {
                    MyLog.i(this, msgLog);
                }
            } catch (InstantiationException e) {
                MyLog.e(this, "Error at version " + currentVersion, e);
            } catch (IllegalAccessException e) {
                MyLog.e(this, "Error at version " + currentVersion, e);
            }
        } while (oneStep != null && currentVersion < newVersion);

        if (currentVersion == newVersion) {
            MyLog.i(this, "Successfully upgraded database from version " + oldVersion + " to version "
                    + newVersion + ".");
        } else {
            String msgLog;
            if (converterNotFound) {
                msgLog = "This version of application doesn't support database upgrade";
            } else {
                msgLog = "Error upgrading database";
            }
            msgLog += " from version " + oldVersion + " to version "
                    + newVersion + ". Current database version=" + currentVersion
                    + " \n" + "Error: " + lastError;

            MyLog.e(this, msgLog);
            throw new ApplicationUpgradeException(msgLog);
        }
    }

    private static abstract class OneStep {
        SQLiteDatabase db;
        int oldVersion;
        int versionTo;
        String sql = "";
        protected String lastError = "?";

        int execute (SQLiteDatabase db, int oldVersion) {
            boolean ok = false;
            this.db = db;
            this.oldVersion = oldVersion;
            try {
                MyLog.i(this, "Database upgrading step from version " + oldVersion + " to version " + versionTo);
                execute2();
                ok = true;
            } catch (Exception e) {
                lastError = e.getMessage();
                MyLog.e(this, e);
            }
            if (ok) {
                MyLog.i(this, "Database upgrading step successfully upgraded database from "
                        + oldVersion + " to version " + versionTo);
            } else {
                MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion
                        + " to version " + versionTo
                        + " SQL='" + sql + "'"
                        + " Error: " + lastError);
            }
            return ok ? versionTo : oldVersion;
        }

        protected abstract void execute2();

        public String getLastError() {
            return lastError;
        }
    }

    static class Convert14 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 15;
            sql = "ALTER TABLE msg ADD COLUMN public BOOLEAN DEFAULT 0 NOT NULL";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE msg SET public=0";
            DbUtils.execSQL(db, sql);
        }
    }

    static class Convert15 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 16;
            boolean ok = MyAccountConverter.convert14to16(db, oldVersion) == versionTo;
            if (ok) {
                sql = "DELETE FROM Origin WHERE _ID IN(6, 7)";
                DbUtils.execSQL(db, sql);
            }
        }
    }

    static class Convert16 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 17;
            File avatarsDir = MyStorage.getDataFilesDir("avatars");
            if (avatarsDir.exists()) {
                FileUtils.deleteFilesRecursively(avatarsDir);
                if (!avatarsDir.delete()) {
                    MyLog.e(this, "Couldn't delete " + avatarsDir.getAbsolutePath());
                }
            }
            sql = "DROP TABLE avatar";
            DbUtils.execSQL(db, sql);
            sql = "CREATE TABLE download (_id INTEGER PRIMARY KEY AUTOINCREMENT,download_type INTEGER NOT NULL,user_id INTEGER,msg_id INTEGER,content_type INTEGER NOT NULL,valid_from INTEGER NOT NULL,url TEXT NOT NULL,loaded_date INTEGER,download_status INTEGER NOT NULL DEFAULT 0,file_name TEXT)";
            DbUtils.execSQL(db, sql);
            sql = "CREATE INDEX idx_download_user ON download (user_id, download_status)";
            DbUtils.execSQL(db, sql);
            sql = "CREATE INDEX idx_download_msg ON download (msg_id, content_type, download_status)";
            DbUtils.execSQL(db, sql);

            sql = "ALTER TABLE origin RENAME TO oldorigin";
            DbUtils.execSQL(db, sql);
            sql = "DROP INDEX idx_origin_name";
            DbUtils.execSQL(db, sql);

            sql = "CREATE TABLE origin (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_type_id INTEGER NOT NULL,origin_name TEXT NOT NULL,origin_url TEXT NOT NULL,ssl BOOLEAN DEFAULT 0 NOT NULL,allow_html BOOLEAN DEFAULT 0 NOT NULL,text_limit INTEGER NOT NULL,short_url_length INTEGER NOT NULL DEFAULT 0)";
            DbUtils.execSQL(db, sql);
            sql = "CREATE UNIQUE INDEX idx_origin_name ON origin (origin_name)";
            DbUtils.execSQL(db, sql);
            sql = "INSERT INTO origin (_id, origin_type_id, origin_name, origin_url, ssl, allow_html, text_limit, short_url_length)" +
                    " SELECT _id, origin_type_id, origin_name, host, ssl, allow_html, text_limit, short_url_length" +
                    " FROM oldorigin";
            DbUtils.execSQL(db, sql);
            sql = "DROP TABLE oldorigin";
            DbUtils.execSQL(db, sql);
        }
    }

    static class Convert17 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 18 ;

            sql = "DROP INDEX IF EXISTS idx_username";
            DbUtils.execSQL(db, sql);

            sql = "CREATE INDEX idx_user_origin ON user (origin_id, user_oid)";
            DbUtils.execSQL(db, sql);

            sql = "ALTER TABLE user ADD COLUMN webfinger_id TEXT";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE user SET webfinger_id=username";
            DbUtils.execSQL(db, sql);
        }
    }

    static class Convert18 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 19;

            sql = "CREATE INDEX idx_msg_sent_date ON msg (msg_sent_date)";
            DbUtils.execSQL(db, sql);
        }
    }

    static class Convert19 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 20;

            sql = "ALTER TABLE origin ADD COLUMN ssl_mode INTEGER DEFAULT 1";
            DbUtils.execSQL(db, sql);
            sql = "ALTER TABLE origin ADD COLUMN in_combined_global_search BOOLEAN DEFAULT 1";
            DbUtils.execSQL(db, sql);
            sql = "ALTER TABLE origin ADD COLUMN in_combined_public_reload BOOLEAN DEFAULT 1";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE origin SET ssl_mode=1, in_combined_global_search=1, in_combined_public_reload=1";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE origin SET ssl_mode=2 WHERE origin_url LIKE '%quitter.zone%'";
            DbUtils.execSQL(db, sql);
        }
    }

    static class Convert20 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 21;

            sql = "ALTER TABLE origin ADD COLUMN mention_as_webfinger_id INTEGER DEFAULT 3";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE origin SET mention_as_webfinger_id=3";
            DbUtils.execSQL(db, sql);
            sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (in_reply_to_msg_id)";
            DbUtils.execSQL(db, sql);
        }
    }

    static class Convert21 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 22;

            sql = "ALTER TABLE origin ADD COLUMN use_legacy_http INTEGER DEFAULT 3";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE origin SET use_legacy_http=3";
            DbUtils.execSQL(db, sql);
        }
    }

    static class Convert22 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 23;

            sql = "ALTER TABLE msg ADD COLUMN msg_status INTEGER NOT NULL DEFAULT 0";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE msg SET msg_status=0";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE msg SET msg_status=2 WHERE msg_created_date IS NOT NULL";
            DbUtils.execSQL(db, sql);


        }
    }

    static class Convert23 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 24;

            sql = "DROP TABLE IF EXISTS newuser";
            DbUtils.execSQL(db, sql);

            sql = "UPDATE user SET user_created_date = 0 WHERE user_created_date IS NULL";
            DbUtils.execSQL(db, sql);
            sql = "UPDATE user SET user_oid = ('andstatustemp:' || _id) WHERE user_oid IS NULL";
            DbUtils.execSQL(db, sql);

            sql = "CREATE TABLE newuser (_id INTEGER PRIMARY KEY AUTOINCREMENT,origin_id INTEGER NOT NULL,user_oid TEXT,username TEXT NOT NULL,webfinger_id TEXT NOT NULL,real_name TEXT,user_description TEXT,location TEXT,profile_url TEXT,homepage TEXT,avatar_url TEXT,banner_url TEXT,msg_count INTEGER DEFAULT 0 NOT NULL,favorited_count INTEGER DEFAULT 0 NOT NULL,following_count INTEGER DEFAULT 0 NOT NULL,followers_count INTEGER DEFAULT 0 NOT NULL,user_created_date INTEGER,user_updated_date INTEGER,user_ins_date INTEGER NOT NULL,home_timeline_position TEXT DEFAULT '' NOT NULL,home_timeline_item_date INTEGER DEFAULT 0 NOT NULL,home_timeline_date INTEGER DEFAULT 0 NOT NULL,favorites_timeline_position TEXT DEFAULT '' NOT NULL,favorites_timeline_item_date INTEGER DEFAULT 0 NOT NULL,favorites_timeline_date INTEGER DEFAULT 0 NOT NULL,direct_timeline_position TEXT DEFAULT '' NOT NULL,direct_timeline_item_date INTEGER DEFAULT 0 NOT NULL,direct_timeline_date INTEGER DEFAULT 0 NOT NULL,mentions_timeline_position TEXT DEFAULT '' NOT NULL,mentions_timeline_item_date INTEGER DEFAULT 0 NOT NULL,mentions_timeline_date INTEGER DEFAULT 0 NOT NULL,user_timeline_position TEXT DEFAULT '' NOT NULL,user_timeline_item_date INTEGER DEFAULT 0 NOT NULL,user_timeline_date INTEGER DEFAULT 0 NOT NULL,following_user_date INTEGER DEFAULT 0 NOT NULL,followers_user_date INTEGER DEFAULT 0 NOT NULL,user_msg_id INTEGER DEFAULT 0 NOT NULL,user_msg_date INTEGER DEFAULT 0 NOT NULL)";
            DbUtils.execSQL(db, sql);
            sql = "INSERT INTO newuser (" +
                    " _id, origin_id, user_oid, username, webfinger_id, real_name, user_description, location," +
                    " profile_url, homepage, avatar_url, banner_url," +
                    " msg_count, favorited_count, following_count, followers_count," +
                    " user_created_date, user_updated_date, user_ins_date," +
                    " home_timeline_position, home_timeline_item_date, home_timeline_date, favorites_timeline_position, favorites_timeline_item_date, favorites_timeline_date, direct_timeline_position, direct_timeline_item_date, direct_timeline_date, mentions_timeline_position, mentions_timeline_item_date, mentions_timeline_date, user_timeline_position, user_timeline_item_date, user_timeline_date," +
                    " following_user_date, followers_user_date, user_msg_id, user_msg_date" +
                    ") SELECT " +
                    " _id, origin_id, user_oid, username, webfinger_id, real_name, user_description, NULL," +
                    " url,         homepage, avatar_url, NULL," +
                    "         0,               0,               0,               0," +
                    " user_created_date,                 0, user_ins_date," +
                    " home_timeline_position, home_timeline_item_date, home_timeline_date, favorites_timeline_position, favorites_timeline_item_date, favorites_timeline_date, direct_timeline_position, direct_timeline_item_date, direct_timeline_date, mentions_timeline_position, mentions_timeline_item_date, mentions_timeline_date, user_timeline_position, user_timeline_item_date, user_timeline_date," +
                    " following_user_date,                   0, user_msg_id, user_msg_date" +
                    " FROM user";
            DbUtils.execSQL(db, sql);

            sql = "DROP INDEX idx_user_origin";
            DbUtils.execSQL(db, sql);
            sql = "DROP TABLE user";
            DbUtils.execSQL(db, sql);

            sql = "ALTER TABLE newuser RENAME TO user";
            DbUtils.execSQL(db, sql);
            try {
                sql = "CREATE UNIQUE INDEX idx_user_origin ON user (origin_id, user_oid)";
                DbUtils.execSQL(db, sql);
            } catch (Exception e) {
                MyLog.i(this, "Couldn't create unique constraint", e);
                sql = "CREATE INDEX idx_user_origin ON user (origin_id, user_oid)";
                DbUtils.execSQL(db, sql);
            }
        }
    }

    static class Convert24 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 25;

            sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type STRING NOT NULL,account_id INTEGER,user_id INTEGER,user_in_timeline TEXT,origin_id INTEGER,search_query TEXT,is_synced_automatically BOOLEAN DEFAULT 0 NOT NULL,displayed_in_selector INTEGER DEFAULT 0 NOT NULL,selector_order INTEGER DEFAULT 0 NOT NULL,sync_succeeded_date INTEGER,sync_failed_date INTEGER,error_message TEXT,synced_times_count INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count INTEGER DEFAULT 0 NOT NULL,downloaded_items_count INTEGER DEFAULT 0 NOT NULL,new_items_count INTEGER DEFAULT 0 NOT NULL,count_since INTEGER,synced_times_count_total INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count_total INTEGER DEFAULT 0 NOT NULL,downloaded_items_count_total INTEGER DEFAULT 0 NOT NULL,new_items_count_total INTEGER DEFAULT 0 NOT NULL,youngest_position TEXT,youngest_item_date INTEGER,youngest_synced_date INTEGER,oldest_position TEXT,oldest_item_date INTEGER,oldest_synced_date INTEGER,visible_item_id INTEGER,visible_y INTEGER,visible_oldest_date INTEGER)";
            DbUtils.execSQL(db, sql);

            sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN DEFAULT 0 NOT NULL,manually_launched BOOLEAN DEFAULT 0 NOT NULL,timeline_id INTEGER,timeline_type STRING,account_id INTEGER,user_id INTEGER,origin_id INTEGER,search_query TEXT,item_id INTEGER,username TEXT,last_executed_date INTEGER,execution_count INTEGER DEFAULT 0 NOT NULL,retries_left INTEGER DEFAULT 0 NOT NULL,num_auth_exceptions INTEGER DEFAULT 0 NOT NULL,num_io_exceptions INTEGER DEFAULT 0 NOT NULL,num_parse_exceptions INTEGER DEFAULT 0 NOT NULL,error_message TEXT,downloaded_count INTEGER DEFAULT 0 NOT NULL,progress_text TEXT)";
            DbUtils.execSQL(db, sql);
        }
    }

    static class Convert25 extends OneStep {
        Convert25() {
            versionTo = 26;
        }

        @Override
        protected void execute2() {
            sql = "DROP INDEX idx_msg_in_reply_to_msg_id";
            DbUtils.execSQL(db, sql);

            sql = "ALTER TABLE msg ADD COLUMN conversation_id INTEGER";
            DbUtils.execSQL(db, sql);
            sql = "ALTER TABLE msg ADD COLUMN conversation_oid TEXT";
            DbUtils.execSQL(db, sql);
            sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (" + "in_reply_to_msg_id" + ")" +
                    (Build.VERSION.SDK_INT >= PARTIAL_INDEX_SUPPORTED ?
                            " WHERE " + "in_reply_to_msg_id" + " IS NOT NULL" : "");
            DbUtils.execSQL(db, sql);
            sql = "CREATE INDEX idx_msg_conversation_id ON msg (" + "conversation_id" + ")" +
                    (Build.VERSION.SDK_INT >= PARTIAL_INDEX_SUPPORTED ?
                            " WHERE " + "conversation_id" + " IS NOT NULL" : "");
            DbUtils.execSQL(db, sql);
        }
    }

}
