/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.content.ContentValues;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.SqlActorIds;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;

import java.util.HashSet;
import java.util.Set;

public class Audience {
    private final Set<Actor> recipients = new HashSet<>();

    public static Audience fromMsgId(@NonNull Origin origin, long msgId) {
        String where = AudienceTable.MSG_ID + "=" + msgId;
        String sql = "SELECT " + AudienceTable.USER_ID
                + " FROM " + AudienceTable.TABLE_NAME
                + " WHERE " + where;
        Audience audience = new Audience();
        for (long recipientId : MyQuery.getLongs(sql)) {
            audience.add(Actor.fromOriginAndActorId(origin, recipientId));
        }
        return audience;
    }

    public Actor getFirst() {
        if (recipients.isEmpty()) {
            return Actor.EMPTY;
        }
        return recipients.iterator().next();
    }

    public String getUserNames() {
        StringBuilder sb = new StringBuilder();
        for (Actor actor : recipients) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(actor.getTimelineUsername());
        }
        return sb.toString();
    }

    public Set<Actor> getRecipients() {
        return recipients;
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return recipients.isEmpty();
    }

    public void addAll(@NonNull Audience audience) {
        for (Actor actor : audience.recipients) {
            add(actor);
        }
    }

    public void add(@NonNull Actor actor) {
        if (!recipients.contains(actor)) {
            recipients.add(actor);
            return;
        }
        if (actor.isPartiallyDefined()) {
            return;
        }
        recipients.remove(actor);
        recipients.add(actor);
    }

    public boolean containsMe(MyContext myContext) {
        return myContext.persistentAccounts().contains(recipients);
    }

    @NonNull
    public boolean contains(Actor actor) {
        for (Actor recipient : recipients) {
            if (recipient.equals(actor)) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(long actorId) {
        for (Actor recipient : recipients) {
            if (recipient.actorId == actorId) {
                return true;
            }
        }
        return false;
    }

    public void save(@NonNull MyContext myContext, @NonNull Origin origin, long msgId) {
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null || !origin.isValid() || msgId == 0) {
            return;
        }
        Audience prevAudience = Audience.fromMsgId(origin, msgId);
        Set<Actor> toDelete = new HashSet<>();
        Set<Actor> toAdd = new HashSet<>();
        for (Actor recipient : prevAudience.getRecipients()) {
            if (!getRecipients().contains(recipient)) {
                toDelete.add(recipient);
            }
        }
        for (Actor actor : getRecipients()) {
            if (!prevAudience.getRecipients().contains(actor)) {
                if (actor.actorId == 0) {
                    MyLog.w(this, "No actorId for " + actor);
                } else {
                    toAdd.add(actor);
                }
            }
        }
        try {
            if (!toDelete.isEmpty()) {
                db.delete(AudienceTable.TABLE_NAME, AudienceTable.MSG_ID + "=" + msgId
                        + " AND " + AudienceTable.USER_ID + SqlActorIds.fromUsers(toDelete).getSql(), null);
            }
            for (Actor actor : toAdd) {
                ContentValues values = new ContentValues();
                values.put(AudienceTable.MSG_ID, msgId);
                values.put(AudienceTable.USER_ID, actor.actorId);
                long rowId = db.insert(AudienceTable.TABLE_NAME, null, values);
                if (rowId == -1) {
                    throw new SQLException("Failed to insert " + actor);
                }
            }
        } catch (Exception e) {
            MyLog.e(this, "save, msgId:" + msgId + "; " + recipients, e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Audience audience = (Audience) o;

        return recipients.equals(audience.recipients);
    }

    @Override
    public int hashCode() {
        return recipients.hashCode();
    }

    @Override
    public String toString() {
        return recipients.toString();
    }
}
