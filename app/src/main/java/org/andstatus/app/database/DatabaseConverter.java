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
import android.app.ProgressDialog;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.R;
import org.andstatus.app.account.AccountData;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccountConverter;
import org.andstatus.app.account.PersistentAccounts;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyStorage;
import org.andstatus.app.data.ApplicationUpgradeException;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.util.DialogFactory;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;

import java.io.File;
import java.util.Set;

class DatabaseConverter {
    long startTime = java.lang.System.currentTimeMillis();
    private Activity activity;
    private ProgressDialog progress = null;

    protected boolean execute(DatabaseConverterController.UpgradeParams params) {
        boolean success = false;
        activity = params.upgradeRequestor;
        String msgLog = "";
        long endTime = 0;
        try {
            upgradeStarted();
            convertAll(params.db, params.oldVersion, params.newVersion);
            success = true;
            endTime = java.lang.System.currentTimeMillis();
        } catch (ApplicationUpgradeException e) {
            endTime = java.lang.System.currentTimeMillis();
            closeProgressDialog();
            msgLog = e.getMessage();
            showProgressDialog(msgLog);
            MyLog.ignored(this, e);
            mySleepWithLogging(30000);
        } finally {
            mySleepWithLogging(1500);
            upgradeEnded();
            if (MyContextHolder.get().isTestRun()) {
                activity.finish();
            }
            mySleepWithLogging(500);
        }
        if (success) {
            MyLog.w(this, "Upgrade successfully completed in "
                    + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                    + " seconds");
        } else {
            String msgLog2 = "Upgrade failed in "
                    + java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                    + " seconds";
            MyLog.e(this, msgLog2);
        }
        return success;
    }

    private void mySleepWithLogging(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception e) {
            MyLog.d(this, "while sleeping", e);
        }
    }

    private void upgradeStarted() {
        showProgressDialog(activity.getText(R.string.label_upgrading));
    }

    private void showProgressDialog(final CharSequence message) {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progress = new ProgressDialog(activity, ProgressDialog.STYLE_SPINNER);
                    progress.setTitle(R.string.app_name);
                    progress.setMessage(message);
                    progress.show();
                }
            });
        } catch (Exception e) {
            MyLog.d(this, "upgradeStarted", e);
        }
    }

    private void upgradeEnded() {
        closeProgressDialog();
    }

    private void closeProgressDialog() {
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DialogFactory.dismissSafely(progress);
                }
              });
        } catch (Exception e) {
            MyLog.d(this, "upgradeEnded", e);
        }
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
                MyLog.e(this, "Couldn't create unique constraint");
                sql = "CREATE INDEX idx_user_origin ON user (origin_id, user_oid)";
                DbUtils.execSQL(db, sql);
            }
        }
    }

    static class Convert24 extends OneStep {
        @Override
        protected void execute2() {
            versionTo = 25;

            sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_name TEXT,timeline_description TEXT,timeline_type STRING NOT NULL,all_origin BOOLEAN DEFAULT 0 NOT NULL,origin_id INTEGER,account_id INTEGER,user_id INTEGER,search_query TEXT,synced BOOLEAN DEFAULT 1 NOT NULL,display_in_selector BOOLEAN DEFAULT 1 NOT NULL,selector_order INTEGER DEFAULT 1 NOT NULL,synced_date INTEGER,sync_failed_date INTEGER,error_message TEXT,synced_times_count INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count INTEGER DEFAULT 0 NOT NULL,new_items_count INTEGER DEFAULT 0 NOT NULL,count_since INTEGER,synced_times_count_total INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count_total INTEGER DEFAULT 0 NOT NULL,new_items_count_total INTEGER DEFAULT 0 NOT NULL,youngest_position TEXT,youngest_item_date INTEGER,youngest_synced_date INTEGER,oldest_position TEXT,oldest_item_date INTEGER,oldest_synced_date INTEGER)";
            DbUtils.execSQL(db, sql);

            sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,account_id INTEGER,timeline_type STRING,timeline_id INTEGER,in_foreground BOOLEAN DEFAULT 0 NOT NULL,manually_launched BOOLEAN DEFAULT 0 NOT NULL,item_id INTEGER,command_description TEXT,search_query TEXT,user_id INTEGER,username TEXT,last_executed_date INTEGER,execution_count INTEGER DEFAULT 0 NOT NULL,retries_left INTEGER DEFAULT 0 NOT NULL,num_auth_exceptions INTEGER DEFAULT 0 NOT NULL,num_io_exceptions INTEGER DEFAULT 0 NOT NULL,num_parse_exceptions INTEGER DEFAULT 0 NOT NULL,error_message TEXT,downloaded_count INTEGER DEFAULT 0 NOT NULL,progress_text TEXT)";
            DbUtils.execSQL(db, sql);

            Set<AccountData> accountDataSet = PersistentAccounts.getAccountDataFromAccountManager(MyContextHolder.get());
            for (AccountData accountData : accountDataSet) {
                long accountId = accountData.getDataLong(MyAccount.KEY_USER_ID, 0);
                sql = "SELECT origin_id FROM user WHERE _id=" + accountId;
                long originId = MyQuery.sqlToLong(db, sql, sql);
                if (originId != 0) {
                    for (TimelineType timelineType : TimelineType.defaultTimelineTypes) {
                        insertTimeline(timelineType, originId, accountId);
                    }
                }
            }
        }

        private void insertTimeline(TimelineType timelineType, long originId, long userId) {
            sql = "INSERT INTO timeline (timeline_type, origin_id, account_id" +
                    ", selector_order, synced)"
                    + " VALUES('" + timelineType.save() + "', "
                    + originId + ", " + userId + ", "
                    + timelineType.ordinal() + ", "
                    + (timelineType.isSyncableByDefault() ? "1" : "0") + ")";
            DbUtils.execSQL(db, sql);
        }
    }
}
