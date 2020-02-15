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

import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.user.User;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.andstatus.app.util.StringUtil.toTempOid;

/**
 * @author yvolk@yurivolkov.com
 */
class CheckUsers extends DataChecker {

    private static class CheckResults {
        Map<String, Set<Actor>> actorsToMergeUsers = new HashMap<>();
        Set<User> usersToSave = new HashSet<>();
        Set<Actor> actorsWithoutUsers = new HashSet<>();
        Set<Actor> actorsToFixWebFingerId = new HashSet<>();
        Set<Actor> actorsWithoutOrigin = new HashSet<>();
        List<String> problems = new ArrayList<>();
    }

    @Override
    long fixInternal() {
        CheckResults results = getResults();
        logResults(results);
        if (countOnly) return results.problems.size();

        long changedCount = 0;
        for (User user : results.usersToSave) {
            user.save(myContext);
            changedCount++;
        }
        changedCount += results.actorsToMergeUsers.values().stream().mapToLong(this::mergeUsers).sum();
        for (Actor actor : results.actorsWithoutOrigin) {
            Actor parent = actor.getParent();
            if (parent.nonEmpty()) {
                // The error affects development database only
                String groupUsername = actor.groupType.name + ".of." + parent.getUsername() + "." + parent.actorId;
                String groupTempOid = toTempOid(groupUsername);

                String sql = "UPDATE " + ActorTable.TABLE_NAME + " SET " +
                        ActorTable.ORIGIN_ID + "=" + parent.origin.getId() + ", " +
                        ActorTable.USERNAME + "='" + groupUsername + "', " +
                        ActorTable.ACTOR_OID + "='" + groupTempOid + "'" +
                        " WHERE " + ActorTable._ID + "=" + actor.actorId;
                myContext.getDatabase().execSQL(sql);
                changedCount++;
            } else {
                MyLog.w(this, "Couldn't fix origin for " + actor);
            }
        }
        for (Actor actor : results.actorsToFixWebFingerId) {
            String sql = "UPDATE " + ActorTable.TABLE_NAME + " SET " + ActorTable.WEBFINGER_ID + "='"
                    + actor.getWebFingerId() + "' WHERE " + ActorTable._ID + "=" + actor.actorId;
            myContext.getDatabase().execSQL(sql);
            changedCount++;
        }
        for (Actor actor1 : results.actorsWithoutUsers) {
            Actor actor = actor1.lookupUser();
            actor.saveUser();
            String sql = "UPDATE " + ActorTable.TABLE_NAME + " SET " + ActorTable.USER_ID + "="
                    + actor.user.userId + " WHERE " + ActorTable._ID + "=" + actor.actorId;
            myContext.getDatabase().execSQL(sql);
            changedCount++;
        }
        return changedCount;
    }

    private void logResults(CheckResults results) {
        if (results.problems.isEmpty()) {
            MyLog.d(this, "No problems found");
            return;
        }
        MyLog.i(this, "Problems found: " + results.problems.size());
        IntStream.range(0, results.problems.size())
                .mapToObj(i -> Integer.toString(i + 1) + ". " + results.problems.get(i))
                .forEachOrdered(s -> MyLog.i(this, s));
    }

    private CheckResults getResults() {
        CheckResults results = new CheckResults();
        String sql = "SELECT " + ActorSql.select(false, true)
                + " FROM " + ActorSql.tables(false, true)
                + " ORDER BY " + ActorTable.WEBFINGER_ID + " COLLATE NOCASE";
                ;

        long rowsCount = 0;
        MyLog.v(this, () -> sql);
        try (Cursor c = myContext.getDatabase().rawQuery(sql, null)) {
            String key = "";
            Set<Actor> actors = new HashSet<>();
            while (c.moveToNext()) {
                rowsCount++;
                final Actor actor = Actor.fromCursor(myContext, c, false);
                final String webFingerId = DbUtils.getString(c, ActorTable.WEBFINGER_ID);
                if (actor.isWebFingerIdValid() && !actor.getWebFingerId().equals(webFingerId)) {
                    results.actorsToFixWebFingerId.add(actor);
                    results.problems.add("Fix webfingerId: '" + webFingerId + "' " + actor);
                }

                if (myContext.accounts().fromWebFingerId(actor.getWebFingerId()).isValid()
                        && actor.user.isMyUser().untrue) {
                    actor.user.setIsMyUser(TriState.TRUE);
                    results.usersToSave.add(actor.user);
                    results.problems.add("Fix user isMy: " + actor);
                } else if (actor.user.userId == 0) {
                    results.actorsWithoutUsers.add(actor);
                    results.problems.add("Fix userId==0: " + actor);
                }

                if (actor.origin.isEmpty()) {
                    results.actorsWithoutOrigin.add(actor);
                    results.problems.add("Fix no Origin: " + actor);
                }

                if (StringUtil.isEmpty(key) || !actor.getWebFingerId().equals(key)) {
                    if (shouldMerge(actors)) {
                        results.actorsToMergeUsers.put(key, actors);
                        results.problems.add("Fix merge users 1 \"" + key + "\": " + actors);
                    }
                    key = actor.getWebFingerId();
                    actors = new HashSet<>();
                }
                actors.add(actor);
            }
            if (shouldMerge(actors)) {
                results.actorsToMergeUsers.put(key, actors);
                results.problems.add("Fix merge users 2 \"" + key + "\": " + actors);
            }
        }

        logger.logProgress("Check completed, " + rowsCount + " actors checked." +
                " Users of actors to be merged: " + results.actorsToMergeUsers.size() +
                ", to fix WebfingerId: " +results.actorsToFixWebFingerId.size() +
                ", to add users: " + results.actorsWithoutUsers.size() +
                ", to save users: " + results.usersToSave.size()
        );
        return results;
    }

    private boolean shouldMerge(Set<Actor> actors) {
        return actors.size() > 1 && (actors.stream().anyMatch(a -> a.user.userId == 0)
                || actors.stream().mapToLong(a -> a.user.userId).distinct().count() > 1);
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
