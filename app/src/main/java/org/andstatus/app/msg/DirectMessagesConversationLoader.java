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

package org.andstatus.app.msg;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.data.SelectedUserIds;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
public class DirectMessagesConversationLoader<T extends ConversationItem> extends ConversationLoader<T> {
    public DirectMessagesConversationLoader(Class<T> tClass, MyContext myContext, MyAccount ma,
                                            long selectedMessageId, boolean sync) {
        super(tClass, myContext, ma, selectedMessageId, sync);
    }

    @Override
    protected void load2(T oMsg) {
        long actorId = MyQuery.msgIdToLongColumnValue(ActivityTable.ACTOR_ID, oMsg.getMsgId());
        Audience recipients = Audience.fromMsgId(ma.getOriginId(), oMsg.getMsgId());
        String selection = getSelectionForActorAndRecipient("=" + Long.toString(actorId),
                SelectedUserIds.fromUsers(recipients.getRecipients()).getSql());
        Uri uri = MatchedUri.getTimelineUri(
                Timeline.getTimeline(TimelineType.EVERYTHING, ma, 0, null));
        Cursor cursor = null;
        try {
            cursor = myContext.context().getContentResolver().query(uri, oMsg.getProjection(),
                    selection, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    T oMsg2 = newOMsg(DbUtils.getLong(cursor, BaseColumns._ID));
                    oMsg2.load(cursor);
                    addMessageToList(oMsg2);
                }
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    @NonNull
    // TODO: Actually this is not exactly what we need, because we don't check recipients
    private String getSelectionForActorAndRecipient(String actor, String recipients) {
        return "(" + MsgTable.PRIVATE + "=" + TriState.TRUE.id
                + " AND (" + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + actor
                + " OR " + ProjectionMap.ACTIVITY_TABLE_ALIAS + "." + ActivityTable.ACTOR_ID + recipients + "))";
    }

}
