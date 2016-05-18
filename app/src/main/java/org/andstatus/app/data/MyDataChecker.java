/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor;
import android.support.annotation.NonNull;

import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.DownloadTable;
import org.andstatus.app.database.FriendshipTable;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyDataChecker {
    private final MyContext myContext;
    private final ProgressLogger logger;

    public MyDataChecker(MyContext myContext, ProgressLogger logger) {
        this.myContext = myContext;
        this.logger = logger;
    }

    public void fixData() {
        checkUsers();
    }

    private void checkUsers() {
        final String method = "checkUsers";
        logger.logProgress(method + " started");

        for (MbUser user : getUsersToMerge()) {
            mergeUser(user);
        }
        logger.logProgress(method + " ended");
    }

    private Set<MbUser> getUsersToMerge() {
        final String method = "getUsersToMerge";

        Set<MbUser> usersToMerge = new ConcurrentSkipListSet<>();
        String sql = "SELECT " + UserTable._ID
                + ", " + UserTable.ORIGIN_ID
                + ", " + UserTable.USER_OID
                + ", " + UserTable.WEBFINGER_ID
                + " FROM " + UserTable.TABLE_NAME
                + " ORDER BY " + UserTable.ORIGIN_ID
                + ", " + UserTable.USER_OID
                ;
        Cursor c = null;
        long rowsCount = 0;
        try {
            MbUser prev = null;
            c = myContext.getDatabase().rawQuery(sql, null);
            while (c.moveToNext()) {
                rowsCount++;
                MbUser user = MbUser.fromOriginAndUserOid(c.getLong(1), c.getString(2));
                user.userId = c.getLong(0);
                user.setWebFingerId(c.getString(3));
                if (isTheSameUser(prev, user)) {
                    MbUser userToMerge = whomToMerge(prev, user);
                    usersToMerge.add(userToMerge);
                    prev = userToMerge.actor;
                } else {
                    prev = user;
                }
            }
        } finally {
            DbUtils.closeSilently(c);
        }

        logger.logProgress(method + " ended, " + rowsCount + " users, " + usersToMerge.size() + " to be merged");
        return usersToMerge;
    }

    private boolean isTheSameUser(MbUser prev, MbUser user) {
        if (prev == null || user == null) {
            return false;
        }
        if (prev.originId != user.originId) {
            return false;
        }
        if (!prev.oid.equals(user.oid)) {
            return false;
        }
        return true;
    }

    @NonNull
    private MbUser whomToMerge(@NonNull MbUser prev, @NonNull MbUser user) {
        MbUser mergeWith = prev;
        MbUser toMerge = user;
        if (myContext.persistentAccounts().fromUserId(user.userId).isValid()) {
            mergeWith = user;
            toMerge = prev;
        }
        toMerge.actor = mergeWith;
        return toMerge;
    }

    private void mergeUser(MbUser user) {
        String logMsg = "Merging " + user + " with " + user.actor;
        logger.logProgress(logMsg);
        updateColumn(logMsg, user, MsgTable.TABLE_NAME, MsgTable.SENDER_ID, false);
        updateColumn(logMsg, user, MsgTable.TABLE_NAME, MsgTable.AUTHOR_ID, false);
        updateColumn(logMsg, user, MsgTable.TABLE_NAME, MsgTable.RECIPIENT_ID, false);
        updateColumn(logMsg, user, MsgTable.TABLE_NAME, MsgTable.IN_REPLY_TO_USER_ID, false);

        updateColumn(logMsg, user, MsgOfUserTable.TABLE_NAME, MsgOfUserTable.USER_ID, true);
        deleteRows(logMsg, user, MsgOfUserTable.TABLE_NAME, MsgOfUserTable.USER_ID);

        deleteRows(logMsg, user, FriendshipTable.TABLE_NAME, FriendshipTable.USER_ID);
        deleteRows(logMsg, user, FriendshipTable.TABLE_NAME, FriendshipTable.FRIEND_ID);

        deleteRows(logMsg, user, DownloadTable.TABLE_NAME, DownloadTable.USER_ID);

        deleteRows(logMsg, user, UserTable.TABLE_NAME, UserTable._ID);
    }

    private void updateColumn(String logMsg, MbUser user, String table, String column, boolean ignoreError) {
        String sql = "";
        try {
            sql = "UPDATE "
                    + table
                    + " SET "
                    + column + "=" + user.actor.userId
                    + " WHERE "
                    + column + "=" + user.userId;
            myContext.getDatabase().execSQL(sql);
        } catch (Exception e) {
            if (!ignoreError) {
                logger.logProgress("Error: " + e.getMessage() + ", SQL:" + sql);
                MyLog.e(this, logMsg + ", SQL:" + sql, e);
            }
        }
    }

    private void deleteRows(String logMsg, MbUser user, String table, String column) {
        String sql = "";
        try {
            sql = "DELETE "
                    + " FROM "
                    + table
                    + " WHERE "
                    + column + "=" + user.userId;
            myContext.getDatabase().execSQL(sql);
        } catch (Exception e) {
            logger.logProgress("Error: " + e.getMessage() + ", SQL:" + sql);
            MyLog.e(this, logMsg + ", SQL:" + sql, e);
        }
    }
}
