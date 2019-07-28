/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data.converter;

import android.content.ContentValues;
import android.database.Cursor;

import org.andstatus.app.actor.GroupType;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.andstatus.app.util.StringUtils.toTempOid;

class Convert50 extends ConvertOneStep {
    Convert50() {
        versionTo = 51;
    }

    private static class Data {
        final Map<Long, Set<Long>> friends = new HashMap<>();
        final Map<Long, Set<Long>> followers = new HashMap<>();

        Data addFromCursor(Cursor cursor) {
            long friendId = DbUtils.getLong(cursor, "friend_id");
            long actorId = DbUtils.getLong(cursor, "actor_id");
            if (friendId != 0 && actorId != 0) {
                addMember(friends, actorId, friendId);
                addMember(followers, friendId, actorId);
            }
            return this;
        }

        void addMember(Map<Long, Set<Long>> membership, long groupId, long memberId) {
            Set<Long> members = Optional.ofNullable(membership.get(groupId)).orElseGet(HashSet::new);
            members.add(memberId);
            membership.put(groupId, members);
        }
    }

    @Override
    protected void execute2() {
        progressLogger.logProgress(stepTitle + ": Extending Actor table to hold Group also");
        sql = "ALTER TABLE actor ADD COLUMN group_type INTEGER NOT NULL DEFAULT 0";
        DbUtils.execSQL(db, sql);
        sql = "ALTER TABLE actor ADD COLUMN parent_actor_id INTEGER NOT NULL DEFAULT 0";
        DbUtils.execSQL(db, sql);
        sql = "UPDATE actor SET group_type=0, parent_actor_id=0";
        DbUtils.execSQL(db, sql);

        progressLogger.logProgress(stepTitle + ": Converting Friendship into GroupMembers");

        sql = "CREATE TABLE group_members (group_id INTEGER NOT NULL,member_id INTEGER NOT NULL," +
                " CONSTRAINT pk_group_members PRIMARY KEY (group_id, member_id))";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_membership ON group_members (member_id, group_id)";
        DbUtils.execSQL(db, sql);

        sql ="SELECT * FROM friendship WHERE followed=1";
        Data data = MyQuery.foldLeft(db, sql, new Data(), d -> d::addFromCursor);
        data.friends.entrySet().forEach(groupMembersCreator(GroupType.FRIENDS));
        data.followers.entrySet().forEach(groupMembersCreator(GroupType.FOLLOWERS));

        sql = "DROP TABLE friendship";
        DbUtils.execSQL(db, sql);
    }

    private Consumer<Map.Entry<Long, Set<Long>>> groupMembersCreator(GroupType groupType) {
        return entry -> {
            Long parentActorId = entry.getKey();
            if (entry.getValue().size() < 2) return;

            long originId = MyQuery.actorIdToLongColumnValue("origin_id", parentActorId);
            String parentUsername = MyQuery.actorIdToStringColumnValue("username", parentActorId);
            String groupUsername = groupType.name + ".of." + parentUsername + "." + parentActorId;

            // Add group
            ContentValues values = new ContentValues();
            values.put("parent_actor_id", parentActorId);
            values.put("group_type", groupType.id);
            values.put("origin_id", originId);
            values.put("user_id", 0);
            values.put("actor_oid", toTempOid(groupUsername));
            values.put("username", groupUsername);
            values.put("webfinger_id", "");
            values.put("actor_ins_date", System.currentTimeMillis());
            long groupId = db.insert("actor", "", values);

            // Add members
            entry.getValue().forEach(memberId -> {
                sql = "INSERT INTO group_members (group_id, member_id)" +
                        " VALUES (" + groupId + ", " + memberId + ")";
                DbUtils.execSQL(db, sql);
            });
        };
    }
}
