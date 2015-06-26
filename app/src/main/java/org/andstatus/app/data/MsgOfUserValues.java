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

package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.data.MyDatabase.MsgOfUser;

class MsgOfUserValues {
    private long rowId;
    private long userId;
    private long msgId;
    private ContentValues contentValues = new ContentValues();

    public MsgOfUserValues(long userId) {
        this.userId = userId;
        contentValues.put(MsgOfUser.USER_ID, userId);
    }

    /**
     * Move all keys that belong to MsgOfUser table from values to the newly created ContentValues. 
     * Returns null if we don't need MsgOfUser for this Msg
     * @param values
     * @return
     */
    public static MsgOfUserValues valueOf(long userId, ContentValues values) {
        MsgOfUserValues userValues = new MsgOfUserValues(userId);
        userValues.setMsgId(values.getAsLong(BaseColumns._ID));
        MyQuery.moveBooleanKey(MsgOfUser.SUBSCRIBED, values, userValues.contentValues);
        MyQuery.moveBooleanKey(MsgOfUser.FAVORITED, values, userValues.contentValues);
        MyQuery.moveBooleanKey(MsgOfUser.REBLOGGED, values, userValues.contentValues);
        // The value is String!
        MyQuery.moveStringKey(MsgOfUser.REBLOG_OID, values, userValues.contentValues);
        MyQuery.moveBooleanKey(MsgOfUser.MENTIONED, values, userValues.contentValues);
        MyQuery.moveBooleanKey(MsgOfUser.REPLIED, values, userValues.contentValues);
        MyQuery.moveBooleanKey(MsgOfUser.DIRECTED, values, userValues.contentValues);
        return userValues;
    }

    boolean isValid() {
        return userId != 0 && msgId != 0;
    }
    
    boolean isEmpty() {
        boolean empty = true;
        if (isTrue(MsgOfUser.SUBSCRIBED) 
                || isTrue(MsgOfUser.FAVORITED)
                || isTrue(MsgOfUser.REBLOGGED) 
                || isTrue(MsgOfUser.MENTIONED)
                || isTrue(MsgOfUser.REPLIED) 
                || isTrue(MsgOfUser.DIRECTED) 
                        ) {
            empty = false;
        }
        if (empty && contentValues.containsKey(MsgOfUser.REBLOG_OID)
                && !TextUtils.isEmpty(contentValues.getAsString(MsgOfUser.REBLOG_OID))) {
            empty = false;
        }
        if (!isValid()) {
            empty = true;
        }
        return empty;
    }

    private boolean isTrue(String key) {
        boolean value = false;
        if (contentValues.containsKey(key) ) {
            value = contentValues.getAsInteger(key) != 0;
        }
        return value;
    }
    
    long getUserId() {
        return userId;
    }
    
    public long getMsgId() {
        return msgId;
    }

    public void setMsgId(Long msgIdIn) {
        if (msgIdIn != null) {
            msgId = msgIdIn;
        } else {
            msgId = 0;
        }
        contentValues.put(MsgOfUser.MSG_ID, msgId);
    }
    
    long insert(SQLiteDatabase db) {
        if (!isEmpty()) {
            rowId = db.insert(MsgOfUser.TABLE_NAME, MsgOfUser.MSG_ID, contentValues);
            if (rowId == -1) {
                throw new SQLException("Failed to insert row into " + MsgOfUser.TABLE_NAME);
            }
        }
        return rowId;
    }

    public int update(SQLiteDatabase db) {
        int count = 0;
        if (!isValid()) {
            return count;
        }
        String where = "(" + MsgOfUser.MSG_ID + "=" + msgId + " AND "
                + MsgOfUser.USER_ID + "="
                + userId + ")";
        String sql = "SELECT * FROM " + MsgOfUser.TABLE_NAME + " WHERE "
                + where;
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, null);
            boolean exists = cursor.moveToFirst();
            DbUtils.closeSilently(cursor);
            if (exists) {
                count += db.update(MsgOfUser.TABLE_NAME, contentValues, where, null);
            } else {
                insert(db);
                if (rowId != 0) {
                    count += 1;
                }
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
        return count;
    }
}
