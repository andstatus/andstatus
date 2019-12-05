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

package org.andstatus.app.data.checker;

import android.database.Cursor;

import androidx.annotation.NonNull;

import org.andstatus.app.data.MyProvider;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.AudienceTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.IntStream;

/**
 * @author yvolk@yurivolkov.com
 */
class MergeActors extends DataChecker {

    @Override
    long fixInternal() {
        int changedCount = 0;
        final List<AActivity> actorsToMerge = new ArrayList<>(getActorsToMerge());
        for (AActivity activity : actorsToMerge) {
            MyLog.d(this, "Problems found: " + actorsToMerge.size());
            IntStream.range(0, actorsToMerge.size())
                    .mapToObj(i -> Integer.toString(i) + ". To merge " + actorsToMerge.get(i).getObjActor()
                            + " with " + actorsToMerge.get(i).getActor())
                    .forEachOrdered(s -> MyLog.d(this, s));
            if (!countOnly) {
                mergeActor(activity);
            }
            changedCount++;
        }
        return changedCount;
    }

    private Set<AActivity> getActorsToMerge() {
        final String method = "getActorsToMerge";

        Set<AActivity> mergeActivities = new ConcurrentSkipListSet<>();
        String sql = "SELECT " + ActorTable._ID
                + ", " + ActorTable.ORIGIN_ID
                + ", " + ActorTable.ACTOR_OID
                + ", " + ActorTable.WEBFINGER_ID
                + " FROM " + ActorTable.TABLE_NAME
                + " ORDER BY " + ActorTable.ORIGIN_ID
                + ", " + ActorTable.ACTOR_OID
                ;
        long rowsCount = 0;
        try (Cursor c = myContext.getDatabase().rawQuery(sql, null)) {
            Actor prev = null;
            while (c.moveToNext()) {
                rowsCount++;
                Actor actor = Actor.fromOid(myContext.origins().fromId(c.getLong(1)),
                        c.getString(2));
                actor.actorId = c.getLong(0);
                actor.setWebFingerId(c.getString(3));
                if (isTheSameActor(prev, actor)) {
                    AActivity activity = whomToMerge(prev, actor);
                    mergeActivities.add(activity);
                    prev = activity.getActor();
                } else {
                    prev = actor;
                }
            }
        }

        logger.logProgress(method + " ended, " + rowsCount + " actors, " + mergeActivities.size() + " to be merged");
        return mergeActivities;
    }

    private boolean isTheSameActor(Actor prev, Actor actor) {
        if (prev == null || actor == null) {
            return false;
        }
        if (!prev.origin.equals(actor.origin)) {
            return false;
        }
        if (!prev.oid.equals(actor.oid)) {
            return false;
        }
        return true;
    }

    @NonNull
    private AActivity whomToMerge(@NonNull Actor prev, @NonNull Actor actor) {
        AActivity activity = AActivity.from(Actor.EMPTY, ActivityType.UPDATE);
        activity.setObjActor(actor);
        Actor mergeWith = prev;
        if (myContext.accounts().fromActorId(actor.actorId).isValid()) {
            mergeWith = actor;
            activity.setObjActor(prev);
        }
        activity.setActor(mergeWith);
        return activity;
    }

    private void mergeActor(AActivity activity) {
        String logMsg = "Merging " + activity.getObjActor() + " into " + activity.getActor();
        logger.logProgress(logMsg);
        updateColumn(logMsg, activity, ActivityTable.TABLE_NAME, ActivityTable.ACTOR_ID, false);
        updateColumn(logMsg, activity, ActivityTable.TABLE_NAME, ActivityTable.OBJ_ACTOR_ID, false);

        updateColumn(logMsg, activity, NoteTable.TABLE_NAME, NoteTable.AUTHOR_ID, false);
        updateColumn(logMsg, activity, NoteTable.TABLE_NAME, NoteTable.IN_REPLY_TO_ACTOR_ID, false);

        updateColumn(logMsg, activity, AudienceTable.TABLE_NAME, AudienceTable.ACTOR_ID, true);

        MyProvider.deleteActor(myContext, activity.getObjActor().actorId);
    }

    private void updateColumn(String logMsg, AActivity activity, String tableName, String column, boolean ignoreError) {
        String sql = "";
        try {
            sql = "UPDATE "
                    + tableName
                    + " SET "
                    + column + "=" + activity.getActor().actorId
                    + " WHERE "
                    + column + "=" + activity.getObjActor().actorId;
            myContext.getDatabase().execSQL(sql);
        } catch (Exception e) {
            if (!ignoreError) {
                logger.logProgress("Error: " + e.getMessage() + ", SQL:" + sql);
                MyLog.e(this, logMsg + ", SQL:" + sql, e);
            }
        }
    }
}