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

import android.net.Uri;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.user.UserListType;
import org.andstatus.app.util.MyLog;

public class ParsedUri {
    private final Uri uri;
    private final MatchedUri matchedUri;
    
    private ParsedUri(Uri uri) {
        this.uri = uri == null ? Uri.EMPTY : uri;
        matchedUri = MatchedUri.fromUri(this.uri);
    }
    
    public static ParsedUri fromUri(Uri uri) {
        return new ParsedUri(uri);
    }

    public MatchedUri matched() {
        return matchedUri;
    }
    
    @Override
    public String toString() {
        return "Uri:'" + uri + "'; matched:" + matched();
    }

    public long getAccountUserId() {
        long accountUserId = 0;
        try {
            switch (matchedUri) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_ITEM:
                case MSG_ITEM:
                case ORIGIN_ITEM:
                case USERLIST:
                case USER_ITEM:
                    accountUserId = Long.parseLong(uri.getPathSegments().get(1));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return accountUserId;        
    }
    
    public long getUserId() {
        long userId = 0;
        try {
            switch (matchedUri) {
                case TIMELINE_SEARCH:
                case TIMELINE_ITEM:
                case TIMELINE:
                    userId = Long.parseLong(uri.getPathSegments().get(7));
                    break;
                case USER_ITEM:
                    userId = Long.parseLong(uri.getPathSegments().get(3));
                    break;
                case USERLIST:
                    if (getUserListType() == UserListType.FOLLOWERS) {
                        userId = getItemId();
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.e(this, toString(), e);
        }
        return userId;
    }
    
    public TimelineType getTimelineType() {
        TimelineType tt = TimelineType.UNKNOWN;
        try {
            switch (matchedUri) {
                case TIMELINE:
                case TIMELINE_SEARCH:
                case TIMELINE_ITEM:
                    tt = TimelineType.load(uri.getPathSegments().get(3));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return tt;        
    }

    public UserListType getUserListType() {
        UserListType tt = UserListType.UNKNOWN;
        try {
            switch (matchedUri) {
                case USERLIST:
                    tt = UserListType.load(uri.getPathSegments().get(3));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return tt;        
    }

    /**
     * @return Is the timeline/userlist combined. false for URIs that don't contain such information
     */
    public boolean isCombined() {
        boolean isCombined = false;
        try {
            switch (getTimelineType()) {
            case USER:
                if (MyContextHolder.get().persistentAccounts().isAccountUserId(getUserId())) {
                    isCombined = Long.parseLong(uri.getPathSegments().get(5)) != 0;
                } else {
                    isCombined = true;
                }
                break;
            default:
                switch (matchedUri) {
                    case TIMELINE:
                    case TIMELINE_SEARCH:
                    case TIMELINE_ITEM:
                    case USERLIST:
                        isCombined = Long.parseLong(uri.getPathSegments().get(5)) != 0;
                        break;
                    default:
                        break;
                }
                break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return isCombined;        
    }
    
    public long getMessageId() {
        long messageId = 0;
        try {
            switch (matchedUri) {
                case TIMELINE_ITEM:
                    messageId = Long.parseLong(uri.getPathSegments().get(9));
                    break;
                case MSG_ITEM:
                    messageId = Long.parseLong(uri.getPathSegments().get(3));
                    break;
                case USERLIST:
                    if (getUserListType() == UserListType.USERS_OF_MESSAGE) {
                        messageId = getItemId();
                    }
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        return messageId;        
    }

    public String getSearchQuery() {
        String searchString = "";
        try {
            switch (matchedUri) {
                case TIMELINE_SEARCH:
                    searchString = uri.getPathSegments().get(9);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            MyLog.d(this, toString(), e);
        }
        if (searchString == null) {
            searchString = "";
        }
        return searchString;        
    }

    public long getItemId() {
        switch (getUserListType()) {
            case UNKNOWN:
                switch (getTimelineType()) {
                    case UNKNOWN:
                        return 0;
                    default:
                        return getMessageId();
                }
            default:
                return Long.parseLong(uri.getPathSegments().get(7));
        }
    }
}
