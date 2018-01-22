/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.origin;

import android.net.Uri;
import android.support.annotation.StringRes;

import org.andstatus.app.R;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

class OriginTwitter extends Origin {

    /**
     * In order to comply with Twitter's "Developer Display Requirements" 
     *   https://dev.twitter.com/terms/display-requirements
     * @param resId
     * @return Id of alternative (proprietary) term/phrase
     */
    @Override
    public int alternativeTermForResourceId(@StringRes int resId) {
        int resIdOut;
        switch (resId) {
            case R.string.button_create_message:
                resIdOut = R.string.button_create_message_twitter;
                break;
            case R.string.menu_item_destroy_reblog:
                resIdOut = R.string.menu_item_destroy_reblog_twitter;
                break;
            case R.string.menu_item_reblog:
                resIdOut = R.string.menu_item_reblog_twitter;
                break;
            case R.string.message:
                resIdOut = R.string.message_twitter;
                break;
            case R.string.reblogged_by:
                resIdOut = R.string.reblogged_by_twitter;
                break;
            default:
                resIdOut = resId;
                break;
        }
        return resIdOut;
    }

    @Override
    protected String alternativeMessagePermalink(long messageId) {
        if (url == null) {
            return "";
        }
        final Uri uri = fixUriforPermalink(UriUtils.fromUrl(url));
        if (Audience.fromMsgId(this, messageId).isEmpty()) {
            String userName = MyQuery.msgIdToUsername(MsgTable.AUTHOR_ID, messageId, UserInTimeline.USERNAME);
            final String oid = MyQuery.msgIdToStringColumnValue(MsgTable.MSG_OID, messageId);
            return Uri.withAppendedPath(uri, userName + "/status/" + oid).toString();
        } else {
            return Uri.withAppendedPath(uri, "messages").toString();
        }
    }

    @Override
    public Uri fixUriforPermalink(Uri uri1) {
        Uri uri2 = uri1;
        if( uri2 != null) {
            try {
                if (uri2.getHost().startsWith("api.")) {
                    uri2 = Uri.parse(uri1.toString().replace("//api.", "//"));
                }
            } catch (NullPointerException e) {
                MyLog.d(this, "Malformed Uri from '" + uri2.toString() + "'", e);
            }
        }
        return uri2;
    }
}
