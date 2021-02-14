/*
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import android.content.ContentValues;
import android.database.Cursor;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.CommandTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.LazyVal;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;

import java.util.Objects;

/**
 * Command data about a Timeline. The timeline is lazily evaluated
 * @author yvolk@yurivolkov.com
 */
class CommandTimeline {
    LazyVal<Timeline> timeline;
    private MyContext myContext;
    private long id;
    TimelineType timelineType;
    private long actorId;
    Origin origin;
    String searchQuery;

    static CommandTimeline of(Timeline timeline) {
        CommandTimeline data = new CommandTimeline();
        data.timeline = LazyVal.of(timeline);
        data.myContext = timeline.myContext;
        data.id = timeline.getId();
        data.timelineType = timeline.getTimelineType();
        data.actorId = timeline.getActorId();
        data.origin = timeline.getOrigin();
        data.searchQuery = timeline.getSearchQuery();
        return data;
    }

    static CommandTimeline fromCursor(MyContext myContext, Cursor cursor) {
        CommandTimeline data = new CommandTimeline();
        data.timeline = LazyVal.of(data::evaluateTimeline);
        data.myContext = myContext;
        data.id = DbUtils.getLong(cursor, CommandTable.TIMELINE_ID);
        data.timelineType = TimelineType.load(DbUtils.getString(cursor, CommandTable.TIMELINE_TYPE));
        data.actorId = DbUtils.getLong(cursor, CommandTable.ACTOR_ID);
        data.origin = myContext.origins().fromId(DbUtils.getLong(cursor, CommandTable.ORIGIN_ID));
        data.searchQuery = DbUtils.getString(cursor, CommandTable.SEARCH_QUERY);
        return data;
    }

    private Timeline evaluateTimeline() {
        if (id != 0) return myContext.timelines().fromId(id);

        Actor actor = Actor.load(myContext, actorId);
        return myContext.timelines().get(id, timelineType, actor, origin, searchQuery);
    }

    void toContentValues(ContentValues values) {
        values.put(CommandTable.TIMELINE_ID, getId());
        values.put(CommandTable.TIMELINE_TYPE, timelineType.save());
        values.put(CommandTable.ACTOR_ID, actorId);
        values.put(CommandTable.ORIGIN_ID, origin.getId());
        values.put(CommandTable.SEARCH_QUERY, searchQuery);
    }

    private long getId() {
        return timeline.isEvaluated() ? timeline.get().getId() : id;
    }

    boolean isValid() {
        return timelineType != TimelineType.UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandTimeline data = (CommandTimeline) o;
        return getId() == data.getId() &&
                actorId == data.actorId &&
                timelineType == data.timelineType &&
                origin.equals(data.origin) &&
                searchQuery.equals(data.searchQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), timelineType, actorId, origin, searchQuery);
    }

    @Override
    public String toString() {
        if (timeline.isEvaluated()) return timeline.get().toString();

        MyStringBuilder builder = new MyStringBuilder();
        if (timelineType.isAtOrigin()) {
            builder.withComma(origin.isValid() ? origin.getName() : "(all origins)");
        }
        if (timelineType.isForUser()) {
            if (actorId == 0) {
                builder.withComma("(all accounts)");
        }}
        if (timelineType != TimelineType.UNKNOWN) {
            builder.withComma("type", timelineType.save());
        }
        if (actorId != 0) {
            builder.withComma("actorId", actorId);
        }
        if (StringUtil.nonEmpty(searchQuery)) {
            builder.withCommaQuoted("search", searchQuery, true);
        }
        if ( id != 0) {
            builder.withComma("id", id);
        }
        return builder.toKeyValue("CommandTimeline");
    }
}
