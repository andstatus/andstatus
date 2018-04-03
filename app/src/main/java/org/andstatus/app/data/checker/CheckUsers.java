/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.support.annotation.NonNull;

import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.user.User;
import org.andstatus.app.util.TriState;

import java.util.HashSet;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
class CheckUsers extends DataChecker {

    private static class CheckResults {
        Set<AActivity> usersToMerge = new HashSet<>();
        Set<User> myUsers = new HashSet<>();
    }

    @Override
    long fixInternal(boolean countOnly) {
        int changedCount = 0;
        CheckResults results = getResults();
        for (AActivity activity : results.usersToMerge) {
            mergeUsers(activity);
            changedCount++;
        }
        for (User user : results.myUsers) {
            if (MyQuery.idToLongColumnValue(myContext.getDatabase(), UserTable.TABLE_NAME, UserTable.IS_MY, user.userId)
                    != TriState.TRUE.id) {
                user.save(myContext);
                changedCount++;
            }
        }
        return changedCount;
    }

    private CheckResults getResults() {
        final String method = "getResults";
        CheckResults results = new CheckResults();
        String sql = "SELECT " + ActorTable._ID
                + ", " + ActorTable.ORIGIN_ID
                + ", " + ActorTable.USER_ID
                + ", " + ActorTable.WEBFINGER_ID
                + " FROM " + ActorTable.TABLE_NAME
                + " ORDER BY " + ActorTable.WEBFINGER_ID  +" COLLATE NOCASE"
                ;

        long rowsCount = 0;
        try (Cursor c = myContext.getDatabase().rawQuery(sql, null)) {
            Actor prev = null;
            while (c.moveToNext()) {
                rowsCount++;
                final Actor actor = Actor.fromOriginAndActorId(myContext.origins().fromId(c.getLong(1)),
                        c.getLong(0));
                actor.setWebFingerId(c.getString(3));
                actor.lookupUser(myContext);
                if (shouldMergeUsers(prev, actor)) {
                    AActivity activity = whomToMerge(prev, actor);
                    results.usersToMerge.add(activity);
                    prev = activity.getActor();
                } else {
                    prev = actor;
                }

                if (myContext.accounts().fromWebFingerId(actor.getWebFingerId()).isValid()) {
                    actor.user.setIsMyUser(TriState.TRUE);
                    results.myUsers.add(actor.user);
                }
            }
        }

        logger.logProgress(method + " ended, " + rowsCount + " users, " + results.usersToMerge.size() + " to be merged");
        return results;
    }

    private boolean shouldMergeUsers(Actor prev, Actor actor) {
        if (prev == null || actor == null) return false;
        if (prev.user.userId == actor.user.userId) return false;
        if (!prev.isWebFingerIdValid()) return false;
        return prev.getWebFingerId().equalsIgnoreCase(actor.getWebFingerId());
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

    private void mergeUsers(AActivity activity) {
        Actor actor = activity.getObjActor();
        String logMsg = "Merging user of " + actor + " with " + activity.getActor();
        logger.logProgress(logMsg);
        MyProvider.update(myContext, ActorTable.TABLE_NAME,
                ActorTable.USER_ID + "=" + activity.getActor().user.userId,
                ActorTable.USER_ID + "=" + activity.getObjActor().user.userId
        );
        MyProvider.delete(myContext, UserTable.TABLE_NAME, UserTable._ID + "=" + actor.user.userId);
        actor.user.userId = activity.getActor().user.userId;
        actor.user.setIsMyUser(activity.getActor().user.isMyUser());
    }
}
