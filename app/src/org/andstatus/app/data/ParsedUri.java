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
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.Origin;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.util.MyLog;

public class ParsedUri {
    public static final Uri TIMELINE_URI = Uri.parse("content://" + MatchedUri.AUTHORITY + "/" + MatchedUri.TIMELINE_PATH);
    /**
     * We add this path segment after the {@link #TIMELINE_URI} to form search URI 
     */
    public static final String SEARCH_SEGMENT = "search";
    
    private static final String CONTENT_URI_PREFIX = "content://" + MatchedUri.AUTHORITY + "/";
    /**
     * These are in fact definitions for Timelines based on the Msg table, 
     * not for the Msg table itself.
     * Because we always filter the table by current MyAccount (USER_ID joined through {@link MsgOfUser} ) etc.
     */
    public static final Uri MSG_CONTENT_URI = Uri.parse(CONTENT_URI_PREFIX + Msg.TABLE_NAME);
    public static final Uri MSG_CONTENT_COUNT_URI = Uri.parse(CONTENT_URI_PREFIX + Msg.TABLE_NAME + "/count");
    public static final Uri ORIGIN_CONTENT_URI = Uri.parse(CONTENT_URI_PREFIX + Origin.TABLE_NAME);
    public static final Uri USER_CONTENT_URI = Uri.parse(CONTENT_URI_PREFIX + User.TABLE_NAME);
    
    private final Uri uri;
    private final MatchedUri matchedUri;
    
    private ParsedUri(Uri uri) {
        this.uri = uri == null ? Uri.EMPTY : uri;
        matchedUri = MatchedUri.fromUri(uri);
    }
    
    public static ParsedUri fromUri(Uri uri) {
        return new ParsedUri(uri);
    }

    public MatchedUri matched() {
        return matchedUri;
    }
    
    @Override
    public String toString() {
        return "Uri:\"" + uri + "\"; matched:" + matched();
    }

    public long getAccountUserId() {
        long accountUserId = 0;
        try {
            switch (matchedUri) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_MSG_ID:
                case USERS:
                case USER:
                    accountUserId = Long.parseLong(uri.getPathSegments().get(1));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
        return accountUserId;        
    }
    
    public long getUserId() {
        long userId = 0;
        try {
            switch (matchedUri) {
                case USER:
                    userId = Long.parseLong(uri.getPathSegments().get(3));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.e(this, e);
        }
        return userId;        
    }
    
    /**
     * @param uri URI to decode, e.g. the one built by {@link ParsedUri#getTimelineUri(long, boolean)}
     * @return The timeline combined. 
     */
    public TimelineTypeEnum getTimelineType() {
        TimelineTypeEnum tt = TimelineTypeEnum.UNKNOWN;
        try {
            switch (matchedUri) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_MSG_ID:
                    tt = TimelineTypeEnum.load(uri.getPathSegments().get(3));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, String.valueOf(uri), e);
        }
        return tt;        
    }
    
    /**
     * @return Is the timeline combined. false for URIs that don't contain such information
     */
    public boolean isCombined() {
        boolean isCombined = false;
        try {
            switch (getTimelineType()) {
            case USER:
                isCombined = true;
                break;
            default:
                switch (matchedUri) {
                    case TIMELINE:
                    case TIMELINE_SEARCH:
                    case TIMELINE_MSG_ID:
                        isCombined = ( (Long.parseLong(uri.getPathSegments().get(5)) == 0) ? false : true);
                        break;
                    default:
                        break;
                }
                break;
            }
        } catch (Exception e) {
            MyLog.d(this, String.valueOf(uri), e);
        }
        return isCombined;        
    }
    
    public long getMessageId() {
        long messageId = 0;
        try {
            switch (matchedUri) {
                case TIMELINE_MSG_ID:
                    messageId = Long.parseLong(uri.getPathSegments().get(7));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
        return messageId;        
    }

    public static Uri getOriginUri(long rowId) {
        return ContentUris.withAppendedId(ORIGIN_CONTENT_URI, rowId);
    }

    /**
     * URI of the user as seen from the {@link MyAccount} User point of view
     * @param accountUserId userId of MyAccount
     * @param selectedUserId ID of the selected User; 0 - if the User doesn't exist
     */
    public static Uri getUserUri(long accountUserId, long selectedUserId) {
        Uri uri = ContentUris.withAppendedId(USER_CONTENT_URI, accountUserId);
        uri = Uri.withAppendedPath(uri, "su");
        uri = ContentUris.withAppendedId(uri, selectedUserId);
        return uri;
    }

    /**
     * Build a Timeline URI for this User / {@link MyAccount}
     * @param accountUserId {@link MyDatabase.User#USER_ID}. This user <i>may</i> be an account: {@link MyAccount#getUserId()} 
     * @return
     */
    public static Uri getTimelineUri(long accountUserId, TimelineTypeEnum timelineType, boolean isTimelineCombined) {
        Uri uri = ContentUris.withAppendedId(TIMELINE_URI, accountUserId);
        uri = Uri.withAppendedPath(uri, "tt/" + timelineType.save());
        uri = Uri.withAppendedPath(uri, "combined/" + (isTimelineCombined ? "1" : "0"));
        return uri;
    }

    /**
     * Uri for the message in the account's timeline
     */
    public static Uri getTimelineMsgUri(long accountUserId, TimelineTypeEnum timelineType, boolean isCombined, long msgId) {
        Uri uri = ParsedUri.getTimelineUri(accountUserId, timelineType, isCombined);
        uri = Uri.withAppendedPath(uri,  Msg.TABLE_NAME);
        uri = ContentUris.withAppendedId(uri, msgId);
        return uri;
    }

    public static Uri getTimelineSearchUri(long accountUserId, TimelineTypeEnum timelineType, boolean isCombined, String queryString) {
        Uri uri = ParsedUri.getTimelineUri(accountUserId, timelineType, isCombined);
        if (!TextUtils.isEmpty(queryString)) {
            uri = Uri.withAppendedPath(uri, SEARCH_SEGMENT);
            uri = Uri.withAppendedPath(uri, Uri.encode(queryString));
        }
        return uri;
    }
    
}
