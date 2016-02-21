/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.database.Cursor;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase.Msg;

import java.util.ArrayList;
import java.util.List;

public class ConversationMemberItem extends ConversationItem {
    long authorId = 0;

    @Override
    String[] getProjection() {
        List<String> columnNames = new ArrayList<String>();
        columnNames.add(Msg._ID);
        columnNames.add(Msg.CREATED_DATE);
        columnNames.add(Msg.IN_REPLY_TO_MSG_ID);
        columnNames.add(Msg.AUTHOR_ID);
        return columnNames.toArray(new String[]{});
    }

    @Override
    void load(Cursor cursor) {
        super.load(cursor);
        authorId = DbUtils.getLong(cursor, Msg.AUTHOR_ID);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ConversationMemberItem [authorId=");
        builder.append(authorId);
        builder.append(", ind=");
        builder.append(mListOrder);
        builder.append("]");
        return builder.toString();
    }
}
