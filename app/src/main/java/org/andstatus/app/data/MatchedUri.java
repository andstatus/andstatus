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

import android.content.ContentUris;
import android.content.UriMatcher;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.ClassInApplicationPackage;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.database.DatabaseHolder;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.actor.ActorListType;

/**
 * Classifier of Uri-s, passed to our content provider
 * @author yvolk@yurivolkov.com
 */
public enum MatchedUri {
    /**
     * This Uri is for some Timeline
     */
    TIMELINE(1),
    TIMELINE_SEARCH(3),
    /**
     * The Timeline URI contains Message id 
     */
    TIMELINE_ITEM(4),
    /**
     * Operations on {@link org.andstatus.app.database.table.ActivityTable} and dependent tables
     */
    ACTIVITY(7),
    MSG_ITEM(10),
    ORIGIN(8),
    ORIGIN_ITEM(11),
    /**
     * List of Actors
     */
    ACTORLIST(5),
    ACTORLIST_SEARCH(12),
    ACTORLIST_ITEM(13),
    /**
     * Operations on {@link ActorTable} itself
     */
    ACTOR(6),
    ACTOR_ITEM(9),
    
    UNKNOWN(0);
    
    /**
     * "Authority", represented by this Content Provider and declared in the application's manifest.
     * (see <a href="http://developer.android.com/guide/topics/manifest/provider-element.html">&lt;provider&gt;</a>)
     * 
     * Note: This is historical constant, remained to preserve compatibility without reinstallation
     */
    public static final String AUTHORITY = ClassInApplicationPackage.PACKAGE_NAME + ".data.MyProvider";

    private static final String ORIGIN_SEGMENT = "origin";
    private static final String SEARCH_SEGMENT = "search";
    private static final String LISTTYPE_SEGMENT = "lt";
    private static final String COUNT_SEGMENT = "count";
    private static final String CONTENT_SEGMENT = "content";
    private static final String CONTENT_ITEM_SEGMENT = "item";
    private static final String ACTOR_SEGMENT = "user";
    private static final String CENTRAL_ITEM_SEGMENT = "cnt";

    private static final String CONTENT_URI_PREFIX = "content://" + AUTHORITY + "/";
    public static final Uri ACTIVITY_CONTENT_URI = Uri.parse(CONTENT_URI_PREFIX + ActivityTable.TABLE_NAME + "/" + CONTENT_SEGMENT);

    private final int code;
    
    MatchedUri(int codeIn) {
        code = codeIn;
    }
    
    public static MatchedUri fromUri(Uri uri) {
        if (uri == null || uri.equals(Uri.EMPTY)) {
            return UNKNOWN;
        }
        int codeIn = URI_MATCHER.match(uri);
        for (MatchedUri matched : values()) {
            if (matched.code == codeIn) {
                return matched;
            }
        }
        return UNKNOWN;
    }
    
    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        /** 
         * The order of PathSegments (parameters of timelines) in the URI
         * 1. MyAccount USER_ID is the first parameter (this is his timeline of the type specified below!)
         * 2 - 3. LISTTYPE_SEGMENT + actual type
         * 4 - 5. ORIGIN_SEGMENT +  0 or 1  (1 for combined timeline)
         * 6 - 7. MyDatabase.MSG_TABLE_NAME + "/" + MSG_ID  (optional, used to access specific Message)
         */
        URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + ACTOR_SEGMENT + "/#/" + CONTENT_ITEM_SEGMENT + "/#", TIMELINE_ITEM.code);
        URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + ACTOR_SEGMENT + "/#/" + SEARCH_SEGMENT + "/*", TIMELINE_SEARCH.code);
        URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + ACTOR_SEGMENT + "/#/rnd/#", TIMELINE.code);
        URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + ACTOR_SEGMENT + "/#", TIMELINE.code);
        URI_MATCHER.addURI(AUTHORITY, NoteTable.TABLE_NAME + "/#/" + CONTENT_ITEM_SEGMENT + "/#", MSG_ITEM.code);
        URI_MATCHER.addURI(AUTHORITY, ActivityTable.TABLE_NAME + "/" + CONTENT_SEGMENT, ACTIVITY.code);

        URI_MATCHER.addURI(AUTHORITY, OriginTable.TABLE_NAME + "/#/" + CONTENT_ITEM_SEGMENT + "/#", ORIGIN_ITEM.code);
        URI_MATCHER.addURI(AUTHORITY, OriginTable.TABLE_NAME + "/" + CONTENT_SEGMENT, ORIGIN.code);

        URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + CONTENT_ITEM_SEGMENT + "/#", ACTORLIST_ITEM.code);
        URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + CENTRAL_ITEM_SEGMENT + "/#/" + SEARCH_SEGMENT + "/*", ACTORLIST_SEARCH.code);
        URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + LISTTYPE_SEGMENT + "/*/" + ORIGIN_SEGMENT + "/#/" + CENTRAL_ITEM_SEGMENT + "/#", ACTORLIST.code);
        URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + CONTENT_ITEM_SEGMENT + "/#", ACTOR_ITEM.code);
        URI_MATCHER.addURI(AUTHORITY, ActorTable.TABLE_NAME + "/#/" + CONTENT_SEGMENT, ACTOR.code);
    }
    
    /**
     *  MIME types should be like in android:mimeType in AndroidManifest.xml 
     */
    private static final String CONTENT_TYPE_PREFIX = "vnd.android.cursor.dir/"
            + ClassInApplicationPackage.PACKAGE_NAME + ".provider.";
    private static final String CONTENT_ITEM_TYPE_PREFIX = "vnd.android.cursor.item/"
            + ClassInApplicationPackage.PACKAGE_NAME + ".provider.";
    /**
     * Implements {@link android.content.ContentProvider#getType(Uri)}
     */
    public String getMimeType() {
        String type = null;
        switch (this) {
            case ACTIVITY:
                type = CONTENT_TYPE_PREFIX + ActivityTable.TABLE_NAME;
                break;
            case TIMELINE:
            case TIMELINE_SEARCH:
                type = CONTENT_TYPE_PREFIX + NoteTable.TABLE_NAME;
                break;
            case TIMELINE_ITEM:
            case MSG_ITEM:
                type = CONTENT_ITEM_TYPE_PREFIX + NoteTable.TABLE_NAME;
                break;
            case ORIGIN_ITEM:
                type = CONTENT_ITEM_TYPE_PREFIX + OriginTable.TABLE_NAME;
                break;
            case ACTOR:
            case ACTORLIST:
                type = CONTENT_TYPE_PREFIX + ActorTable.TABLE_NAME;
                break;
            case ACTOR_ITEM:
                type = CONTENT_ITEM_TYPE_PREFIX + ActorTable.TABLE_NAME;
                break;
            default:
                break;
        }
        return type;
    }

    /** Uri for the message in the account's timeline */
    public static Uri getTimelineItemUri(Timeline timeline, long msgId) {
        Uri uri = timeline.getUri();
        uri = Uri.withAppendedPath(uri,  CONTENT_ITEM_SEGMENT);
        uri = ContentUris.withAppendedId(uri, msgId);
        return uri;
    }

    public static Uri getTimelineUri(Timeline timeline) {
        Uri uri = getBaseAccountUri(timeline.getMyAccount().getActorId(), NoteTable.TABLE_NAME);
        uri = Uri.withAppendedPath(uri, LISTTYPE_SEGMENT + "/" + timeline.getTimelineType().save());
        uri = Uri.withAppendedPath(uri, ORIGIN_SEGMENT + "/" + timeline.getOrigin().getId());
        uri = Uri.withAppendedPath(uri, ACTOR_SEGMENT);
        uri = ContentUris.withAppendedId(uri, timeline.getActorId());
        if (!TextUtils.isEmpty(timeline.getSearchQuery())) {
            uri = Uri.withAppendedPath(uri, SEARCH_SEGMENT);
            uri = Uri.withAppendedPath(uri, Uri.encode(timeline.getSearchQuery()));
        }
        return uri;
    }

    public static Uri getMsgUri(long accountUserId, long msgId) {
        return getContentItemUri(accountUserId, NoteTable.TABLE_NAME, msgId);
    }

    /**
     * Build an ActorList Uri for this Actor / {@link MyAccount}
     * @param accountActorId {@link ActorTable#ACTOR_ID}. This actor <i>may</i> be an account: {@link MyAccount#getActorId()}
     * @param searchQuery
     */
    public static Uri getActorListUri(long accountActorId, ActorListType actorListType, long originId, long centralItemId,
                                      String searchQuery) {
        Uri uri = getBaseAccountUri(accountActorId, ActorTable.TABLE_NAME);
        uri = Uri.withAppendedPath(uri, LISTTYPE_SEGMENT + "/" + actorListType.save());
        uri = Uri.withAppendedPath(uri, ORIGIN_SEGMENT + "/" + originId);
        uri = Uri.withAppendedPath(uri, CENTRAL_ITEM_SEGMENT);
        uri = ContentUris.withAppendedId(uri, centralItemId);
        if (!TextUtils.isEmpty(searchQuery)) {
            uri = Uri.withAppendedPath(uri, SEARCH_SEGMENT);
            uri = Uri.withAppendedPath(uri, Uri.encode(searchQuery));
        }
        return uri;
    }

    public static Uri getActorListItemUri(long accountUserId, ActorListType actorListType, long originId, long actorId) {
        Uri uri = getBaseAccountUri(accountUserId, ActorTable.TABLE_NAME);
        uri = Uri.withAppendedPath(uri, LISTTYPE_SEGMENT + "/" + actorListType.save());
        uri = Uri.withAppendedPath(uri, ORIGIN_SEGMENT + "/" + originId);
        uri = Uri.withAppendedPath(uri, CONTENT_ITEM_SEGMENT);
        uri = ContentUris.withAppendedId(uri, actorId);
        return uri;
    }

    public static Uri getActorUri(long accountActorId, long actorId) {
        return getContentItemUri(accountActorId, ActorTable.TABLE_NAME, actorId);
    }

    public static Uri getOriginUri(long originId) {
        return getContentItemUri(0, OriginTable.TABLE_NAME, originId);
    }

    /**
     * @param accountUserId actorId of MyAccount or 0 if not needed
     * @param tableName name in the {@link DatabaseHolder}
     * @param itemId ID or 0 - if the Item doesn't exist
     */
    private static Uri getContentItemUri(long accountUserId, String tableName, long itemId) {
        Uri uri = getBaseAccountUri(accountUserId, tableName); 
        uri = Uri.withAppendedPath(uri, CONTENT_ITEM_SEGMENT);
        uri = ContentUris.withAppendedId(uri, itemId);
        return uri;
    }
    
    private static Uri getBaseAccountUri(long accountUserId, String tableName) {
        return ContentUris.withAppendedId(Uri.parse(CONTENT_URI_PREFIX + tableName), 
                accountUserId);
    }
}