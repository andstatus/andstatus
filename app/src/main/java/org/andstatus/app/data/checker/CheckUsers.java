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

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.user.User;
import org.andstatus.app.util.TriState;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
class CheckUsers extends DataChecker {

    private static class CheckResults {
        Map<String, Set<Actor>> actorsToMergeUsers = new HashMap<>();
        Set<User> usersToSave = new HashSet<>();
        Set<Actor> actorsToFixWebFingerId = new HashSet<>();
    }

    @Override
    long fixInternal(boolean countOnly) {
        CheckResults results = getResults();
        long changedCount = 0;
        for (User user : results.usersToSave) {
            user.save(myContext);
            changedCount++;
        }
        changedCount += results.actorsToMergeUsers.values().stream().mapToLong(this::mergeUsers).sum();
        for (Actor actor : results.actorsToFixWebFingerId) {
            String sql = "UPDATE " + ActorTable.TABLE_NAME + " SET " + ActorTable.WEBFINGER_ID + "='"
                    + actor.getWebFingerId() + "' WHERE " + ActorTable._ID + "=" + actor.actorId;
            myContext.getDatabase().execSQL(sql);
            changedCount++;
        }
        return changedCount;
    }

    private CheckResults getResults() {
        final String method = "getResults";
        CheckResults results = new CheckResults();
        String sql = "SELECT " + ActorTable.TABLE_NAME + "." + ActorTable._ID
                + ", " + ActorTable.ORIGIN_ID
                + ", " + ActorTable.WEBFINGER_ID
                + ", " + UserTable.TABLE_NAME + "." + UserTable._ID + " AS " + UserTable.USER_ID
                + " FROM " + Actor.getActorAndUserSqlTables(true, false)
                + " ORDER BY " + ActorTable.WEBFINGER_ID + " COLLATE NOCASE";
                ;

        long rowsCount = 0;
        try (Cursor c = myContext.getDatabase().rawQuery(sql, null)) {
            String key = "";
            Set<Actor> actors = new HashSet<>();
            while (c.moveToNext()) {
                rowsCount++;
                final Actor actor = Actor.fromOriginAndActorId(
                        myContext.origins().fromId(DbUtils.getLong(c, ActorTable.ORIGIN_ID)),
                        DbUtils.getLong(c, ActorTable._ID));
                final String webFingerId = DbUtils.getString(c, ActorTable.WEBFINGER_ID);
                actor.setWebFingerId(webFingerId);
                if (actor.isWebFingerIdValid() && !actor.getWebFingerId().equals(webFingerId)) {
                    results.actorsToFixWebFingerId.add(actor);
                }

                actor.user = myContext.users().userFromActorId(actor.actorId,
                        () -> new User(
                                DbUtils.getLong(c, UserTable.USER_ID),
                                actor.getWebFingerId(),
                                TriState.FALSE,
                                Collections.emptySet()));
                if (myContext.accounts().fromWebFingerId(actor.getWebFingerId()).isValid()
                        && actor.user.isMyUser().untrue) {
                    actor.user.setIsMyUser(TriState.TRUE);
                    results.usersToSave.add(actor.user);
                } else if (actor.user.userId == 0) {
                    results.usersToSave.add(actor.user);
                }

                if (!actor.getWebFingerId().equals(key)) {
                    if (shouldMerge(actors)) {
                        results.actorsToMergeUsers.put(key, actors);
                    }
                    key = actor.getWebFingerId();
                    actors = new HashSet<>();
                }
                actors.add(actor);
            }
            if (shouldMerge(actors)) {
                results.actorsToMergeUsers.put(key, actors);
            }
        }

        logger.logProgress(method + " ended, " + rowsCount + " actors, " + results.actorsToMergeUsers.size() + " to be merged");
        return results;
    }

    private boolean shouldMerge(Set<Actor> actors) {
        return actors.stream().anyMatch(a -> a.user.userId == 0)
                || actors.stream().mapToLong(a -> a.user.userId).distinct().count() > 1;
    }

    private long mergeUsers(Set<Actor> actors) {
        User userWith = actors.stream().map(a -> a.user).reduce((a, b) -> a.userId < b.userId ? a : b)
                .orElse(User.EMPTY);
        if (userWith.userId == 0) return 0;

        return actors.stream().map( actor -> {
            String logMsg = "Linking " + actor + " with " + userWith;
            if (logger.loggedMoreSecondsAgoThan(5)) logger.logProgress(logMsg);
            MyProvider.update(myContext, ActorTable.TABLE_NAME,
                    ActorTable.USER_ID + "=" + userWith.userId,
                    ActorTable._ID + "=" + actor.actorId
            );
            if (actor.user.userId != 0 && actor.user.userId != userWith.userId && userWith.isMyUser().isTrue) {
                actor.user.setIsMyUser(TriState.FALSE);
                actor.user.save(myContext);
            }
            return 1;
        }).count();
    }
}
