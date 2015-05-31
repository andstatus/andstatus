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

import android.content.UriMatcher;
import android.net.Uri;

import org.andstatus.app.ClassInApplicationPackage;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.Origin;
import org.andstatus.app.data.MyDatabase.User;

/**
 * Classifier of Uri-s, passed to our content provider
 * @author yvolk@yurivolkov.com
 */
public enum MatchedUri {
    /**
     * This first member is for a Timeline of selected User (Account) (or all timelines...) and it corresponds to the {@link #TIMELINE_URI}
     */
    TIMELINE(1),
    /**
     * Operations on {@link MyDatabase.Msg} table itself
     */
    MSG(7),
    MSG_COUNT(2),
    TIMELINE_SEARCH(3),
    /**
     * The Timeline URI contains Message id 
     */
    TIMELINE_MSG_ID(4),
    ORIGIN(8),
    /**
     * Matched code for the list of Users
     */
    USERS(5),
    /**
     * Matched code for the User
     */
    USER(6),
    
    UNKNOWN(0);
    
    /**
     * "Authority", represented by this Content Provider and declared in the application's manifest.
     * (see <a href="http://developer.android.com/guide/topics/manifest/provider-element.html">&lt;provider&gt;</a>)
     * 
     * Note: This is historical constant, remained to preserve compatibility without reinstallation
     */
    public static final String AUTHORITY = ClassInApplicationPackage.PACKAGE_NAME + ".data.MyProvider";

    /**
     * Used for URIs referring to timelines 
     */
    public static final String TIMELINE_PATH = "timeline";

    private int code = 0;
    
    private MatchedUri(int codeIn) {
        code = codeIn;
    }
    
    public static MatchedUri fromUri(Uri uri) {
        int codeIn = URI_MATCHER.match(uri);
        for (MatchedUri matched : MatchedUri.values()) {
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
         * 2 - 3. "tt/" +  {@link MyDatabase.TimelineTypeEnum.save()} - The timeline type 
         * 4 - 5. "combined/" +  0 or 1  (1 for combined timeline) 
         * 6 - 7. MyDatabase.MSG_TABLE_NAME + "/" + MSG_ID  (optional, used to access specific Message)
         */
        URI_MATCHER.addURI(MatchedUri.AUTHORITY, MatchedUri.TIMELINE_PATH + "/#/tt/*/combined/#/search/*", TIMELINE_SEARCH.code);
        URI_MATCHER.addURI(MatchedUri.AUTHORITY, MatchedUri.TIMELINE_PATH + "/#/tt/*/combined/#/" + Msg.TABLE_NAME + "/#", TIMELINE_MSG_ID.code);
        URI_MATCHER.addURI(MatchedUri.AUTHORITY, MatchedUri.TIMELINE_PATH + "/#/tt/*/combined/#", TIMELINE.code);

        URI_MATCHER.addURI(MatchedUri.AUTHORITY, Msg.TABLE_NAME + "/count", MSG_COUNT.code);
        URI_MATCHER.addURI(MatchedUri.AUTHORITY, Msg.TABLE_NAME, MSG.code);

        URI_MATCHER.addURI(MatchedUri.AUTHORITY, Origin.TABLE_NAME, ORIGIN.code);
        
        /** 
         * The order of PathSegments in the URI
         * 1. MyAccount USER_ID is the first parameter (so we can add 'following' information...)
         * 2 - 3. "su/" + USER_ID  (optional, used to access specific User)
         */
        URI_MATCHER.addURI(MatchedUri.AUTHORITY, User.TABLE_NAME + "/#/su/#", USER.code);
        URI_MATCHER.addURI(MatchedUri.AUTHORITY, User.TABLE_NAME + "/#", USERS.code);
    }
    
    /**
     *  Content types should be like in AndroidManifest.xml
     */
    private static final String CONTENT_TYPE_PREFIX = "vnd.android.cursor.dir/"
            + ClassInApplicationPackage.PACKAGE_NAME + ".provider.";
    private static final String CONTENT_ITEM_TYPE_PREFIX = "vnd.android.cursor.item/"
            + ClassInApplicationPackage.PACKAGE_NAME + ".provider.";
    private static final String MSG_CONTENT_TYPE = CONTENT_TYPE_PREFIX + Msg.TABLE_NAME;
    private static final String MSG_CONTENT_ITEM_TYPE = CONTENT_ITEM_TYPE_PREFIX + Msg.TABLE_NAME;
    private static final String ORIGIN_CONTENT_ITEM_TYPE = CONTENT_ITEM_TYPE_PREFIX + Origin.TABLE_NAME;
    private static final String USER_CONTENT_TYPE = CONTENT_TYPE_PREFIX + User.TABLE_NAME;
    private static final String USER_CONTENT_ITEM_TYPE = CONTENT_ITEM_TYPE_PREFIX + User.TABLE_NAME;
    
    /**
     * Implements {@link android.content.ContentProvider#getType(Uri)}
     */
    public String getMimeType() {
        String type = null;
        switch (this) {
            case MSG:
            case TIMELINE:
            case TIMELINE_SEARCH:
            case MSG_COUNT:
                type = MSG_CONTENT_TYPE;
                break;
            case TIMELINE_MSG_ID:
                type = MSG_CONTENT_ITEM_TYPE;
                break;
            case ORIGIN:
                type = ORIGIN_CONTENT_ITEM_TYPE;
                break;
            case USERS:
                type = USER_CONTENT_TYPE;
                break;
            case USER:
                type = USER_CONTENT_ITEM_TYPE;
                break;
            default:
                break;
        }
        return type;
    }
}