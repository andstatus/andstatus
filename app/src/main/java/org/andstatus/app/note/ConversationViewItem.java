/*
 * Copyright (C) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.note;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import android.text.Html;

import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.Set;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class ConversationViewItem extends ConversationItem<ConversationViewItem> {
    public static final ConversationViewItem EMPTY = new ConversationViewItem(true, DATETIME_MILLIS_NEVER);

    private ConversationViewItem(boolean isEmpty, long updatedDate) {
        super(isEmpty, updatedDate);
    }

    private ConversationViewItem(MyContext myContext, Cursor cursor) {
        super(myContext, cursor);
        setOtherViewProperties(cursor);
    }

    @Override
    protected ConversationViewItem newNonLoaded(MyContext myContext, long id) {
        ConversationViewItem item = new ConversationViewItem(false, DATETIME_MILLIS_NEVER);
        item.setMyContext(myContext);
        item.setNoteId(id);
        return item;
    }

    @Override
    Set<String> getProjection() {
        return TimelineSql.getConversationProjection();        
    }

    @Override
    public MyStringBuilder getDetails(Context context, boolean showReceivedTime) {
        MyStringBuilder builder = super.getDetails(context, showReceivedTime);
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            builder.withSpace("(i" + indentLevel + ",r" + replyLevel + ")");
        }
        return builder;
    }

    @NonNull
    @Override
    public ConversationViewItem fromCursor(MyContext myContext, Cursor cursor) {
        return new ConversationViewItem(myContext, cursor);
    }
}
