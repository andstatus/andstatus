/* 
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

package org.andstatus.app.data;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.actor.ActorListType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;

public class ParsedUri {
    private final Uri uri;
    private final MatchedUri matchedUri;
    private final String searchQuery;

    private ParsedUri(Intent intent) {
        uri = (intent == null || intent.getData() == null) ? Uri.EMPTY : intent.getData();
        searchQuery = intent == null ? "" : intent.getStringExtra(IntentExtra.SEARCH_QUERY.key);
        matchedUri = MatchedUri.fromUri(this.uri);
    }

    public static ParsedUri fromUri(Uri uri) {
        return fromIntent(new Intent(Intent.ACTION_DEFAULT, uri));
    }

    public static ParsedUri fromIntent(Intent intent) {
        return new ParsedUri(intent);
    }

    public MatchedUri matched() {
        return matchedUri;
    }
    
    @Override
    public String toString() {
        return "Uri:'" + uri + "'; matched:" + matched();
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isEmpty() {
        return uri == Uri.EMPTY;
    }

    public long getAccountActorId() {
        long accountActorId = 0;
        try {
            switch (matchedUri) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_ITEM:
                case NOTE_ITEM:
                case ORIGIN_ITEM:
                case ACTORLIST:
                case ACTORLIST_SEARCH:
                case ACTOR_ITEM:
                    accountActorId = Long.parseLong(uri.getPathSegments().get(1));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return accountActorId;
    }
    
    public long getActorId() {
        long actorId = 0;
        try {
            switch (matchedUri) {
                case TIMELINE_SEARCH:
                case TIMELINE_ITEM:
                case TIMELINE:
                    actorId = Long.parseLong(uri.getPathSegments().get(7));
                    break;
                case ACTOR_ITEM:
                    actorId = Long.parseLong(uri.getPathSegments().get(3));
                    break;
                case ACTORLIST:
                case ACTORLIST_SEARCH:
                    if (getActorListType() == ActorListType.FOLLOWERS) {
                        actorId = getItemId();
                    }
                    break;
                case ACTORLIST_ITEM:
                    actorId = getItemId();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.e(this, toString(), e);
        }
        return actorId;
    }
    
    public TimelineType getTimelineType() {
        try {
            switch (matchedUri) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_ITEM:
                    return TimelineType.load(uri.getPathSegments().get(3));
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return TimelineType.UNKNOWN;
    }

    public ActorListType getActorListType() {
        try {
            switch (matchedUri) {
                case ACTORLIST:
                case ACTORLIST_ITEM:
                case ACTORLIST_SEARCH:
                    return ActorListType.load(uri.getPathSegments().get(3));
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return ActorListType.UNKNOWN;
    }

    public long getOriginId() {
        long originId = 0;
        try {
            if (uri.getPathSegments().size() > 4) {
                originId = Long.parseLong(uri.getPathSegments().get(5));
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return originId;
    }

    public Origin getOrigin(MyContext myContext) {
        return myContext.origins().fromId(getOriginId());
    }

    public long getNoteId() {
        long noteId = 0;
        try {
            switch (matchedUri) {
                case TIMELINE_ITEM:
                    noteId = Long.parseLong(uri.getPathSegments().get(9));
                    break;
                case NOTE_ITEM:
                    noteId = Long.parseLong(uri.getPathSegments().get(3));
                    break;
                case ACTORLIST:
                case ACTORLIST_SEARCH:
                    if (getActorListType() == ActorListType.ACTORS_OF_NOTE) {
                        noteId = getItemId();
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return noteId;
    }

    public boolean isSearch() {
        return StringUtils.nonEmpty(getSearchQuery());
    }

    @NonNull
    public String getSearchQuery() {
        if (StringUtils.nonEmpty(searchQuery)) {
            return searchQuery;
        }
        try {
            switch (matchedUri) {
                case TIMELINE_SEARCH:
                case ACTORLIST_SEARCH:
                    return StringUtils.notNull(uri.getPathSegments().get(9));
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return "";
    }

    public long getItemId() {
        switch (getActorListType()) {
            case UNKNOWN:
                switch (getTimelineType()) {
                    case UNKNOWN:
                        return 0;
                    default:
                        return getNoteId();
                }
            default:
                return Long.parseLong(uri.getPathSegments().get(7));
        }
    }
}
