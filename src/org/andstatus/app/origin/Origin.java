/**
 * Copyright (C) 2012 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;

import org.andstatus.app.R;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.net.Connection.ApiEnum;
import org.andstatus.app.util.MyLog;

/**
 *  Microblogging system (twitter.com, identi.ca, ... ) where messages are being created
 *  (it's the "Origin" of the messages). 
 *  TODO: Currently the class is almost a stub and serves for TWO predefined origins only :-)
 * @author yvolk
 *
 */
public class Origin {
    private static final String TAG = Origin.class.getSimpleName();

    /**
     * Predefined ID for Twitter system 
     * <a href="https://dev.twitter.com/docs">Twitter Developers' documentation</a>
     */
    public static long ORIGIN_ID_TWITTER = 1;
    
    /**
     * Default value for the Originating system mId.
     * TODO: Create a table of these "Origins" ?!
     */
    public static long ORIGIN_ID_DEFAULT = ORIGIN_ID_TWITTER;
    /**
     * Predefined id for the pump.io system 
     * Till July of 2013 (and v.1.16 of AndStatus) the API was: 
     * <a href="http://status.net/wiki/Twitter-compatible_API">Twitter-compatible identi.ca API</a>
     * Since July 2013 the API is <a href="https://github.com/e14n/pump.io/blob/master/API.md">pump.io API</a>
     */
    public static long ORIGIN_ID_PUMPIO = 2;
    /**
     * Name of the default Originating system (it is unique and permanent as an ID).
     */
    public static String ORIGIN_NAME_TWITTER = "twitter";
    public static String ORIGIN_NAME_PUMPIO = "pump.io";

    /** 
     * The URI is consistent with "scheme" and "host" in AndroidManifest
     * Pump.io doesn't work with this scheme: "andstatus-oauth://andstatus.org"
     */
    public static final Uri CALLBACK_URI = Uri.parse("http://oauth-redirect.andstatus.org");
    
    /**
     * Maximum number of characters in the message
     */
    private static int CHARS_MAX_DEFAULT = 140;
    /**
     * Length of the link after changing to the shortened link
     * -1 means that length doesn't change
     * For Twitter.com see <a href="https://dev.twitter.com/docs/api/1.1/get/help/configuration">GET help/configuration</a>
     */
    private static int LINK_LENGTH = 23;
    
    private String name = "";
    private long id = 0;

    /**
     * Default OAuth setting
     */
    private boolean isOAuthDefault = true;
    /**
     * Can OAuth connection setting can be turned on/off from the default setting
     */
    private boolean canChangeOAuth = false;
    private boolean shouldSetNewUsernameManuallyIfOAuth = false;
    /**
     * Can user set username for the new user manually?
     * This is only for no OAuth
     */
    private boolean shouldSetNewUsernameManuallyNoOAuth = false;
    
    private int maxCharactersInMessage = CHARS_MAX_DEFAULT;
    private String usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+";
    
    private OriginConnectionData connectionData = new OriginConnectionData();

    public static Origin fromOriginName(String name) {
        return new Origin(name);
    }
    
    public static Origin fromOriginId(long id) {
        return new Origin(id);
    }

    public static Origin toExistingOrigin(String originName_in) {
        Origin origin = fromOriginName(originName_in);
        if (origin.getId() == 0) {
            origin = fromOriginId(Origin.ORIGIN_ID_DEFAULT);
        }
        return origin;
    }
    
    /**
     * @return the Origin name, unique in the application
     */
    public String getName() {
        return name;
    }

    /**
     * @return the OriginId in MyDatabase. 0 means that this system doesn't exist
     */
    public long getId() {
        return id;
    }

    public ApiEnum getApi() {
        return connectionData.api;
    }

    /**
     * Was this Origin stored for future reuse?
     */
    public boolean isPersistent() {
        return (getId() != 0);
    }
    
    public boolean isOAuthDefault() {
        return isOAuthDefault;
    }

    /**
     * @return the Can OAuth connection setting can be turned on/off from the default setting
     */
    public boolean canChangeOAuth() {
        return canChangeOAuth;
    }

    public boolean isUsernameValid(String username) {
        boolean ok = false;
        if (username != null && (username.length() > 0)) {
            ok = username.matches(usernameRegEx);
            if (!ok && MyLog.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "The Username is not valid: \"" + username + "\" in " + name);
            }
        }
        return ok;
    }
    
    /**
     * @return Can app user set username for the new "Origin user" manually?
     */
    public boolean shouldSetNewUsernameManually(boolean isOAuthUser) {
        if (isOAuthUser) {
            return shouldSetNewUsernameManuallyNoOAuth;
        } else {
            return shouldSetNewUsernameManuallyIfOAuth;
        }
    }

    private Origin(String name_in) {
        name = name_in;
        // TODO: Persistence for Origins
        if (name.compareToIgnoreCase(ORIGIN_NAME_TWITTER) == 0) {
            id = ORIGIN_ID_TWITTER;
            isOAuthDefault = true;
            canChangeOAuth = false;  // Starting from 2010-09 twitter.com allows OAuth only
            shouldSetNewUsernameManuallyIfOAuth = false;
            shouldSetNewUsernameManuallyNoOAuth = false;
            usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+";
            maxCharactersInMessage = CHARS_MAX_DEFAULT;

            connectionData.api = ApiEnum.TWITTER1P1;
            connectionData.isHttps = false;
            connectionData.host = "api.twitter.com";
            connectionData.basicPath = "1.1";
            connectionData.oauthPath = "oauth";
        } else if (name.compareToIgnoreCase(ORIGIN_NAME_PUMPIO) == 0) {
            id = ORIGIN_ID_PUMPIO;
            isOAuthDefault = true;  
            canChangeOAuth = false;
            shouldSetNewUsernameManuallyIfOAuth = true;
            shouldSetNewUsernameManuallyNoOAuth = false;
            usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+@[a-zA-Z_0-9/\\.\\-\\(\\)]+";
            maxCharactersInMessage = 5000; // This is not a hard limit, just for convenience.

            connectionData.api = ApiEnum.PUMPIO;
            connectionData.isHttps = true;
            connectionData.host = "identi.ca";  // Default host
            connectionData.basicPath = "api";
            connectionData.oauthPath = "oauth";
        }
        connectionData.originId = id;
    }
    
    private Origin(long id) {
        this(id == ORIGIN_ID_TWITTER ? ORIGIN_NAME_TWITTER :
                id == ORIGIN_ID_PUMPIO ? ORIGIN_NAME_PUMPIO :
                        "");
    }

    /**
     * Calculates number of Characters left for this message
     * taking shortened URL's length into account.
     * @author yvolk
     */
    public int charactersLeftForMessage(String message) {
        int messageLength = 0;
        if (!TextUtils.isEmpty(message)) {
            messageLength = message.length();
            
            // Now try to adjust the length taking links into account
            SpannableString ss = SpannableString.valueOf(message);
            Linkify.addLinks(ss, Linkify.WEB_URLS);
            URLSpan[] spans = ss.getSpans(0, messageLength, URLSpan.class);
            long nLinks = spans.length;
            for (int ind1=0; ind1 < nLinks; ind1++) {
                int start = ss.getSpanStart(spans[ind1]);
                int end = ss.getSpanEnd(spans[ind1]);
                messageLength += LINK_LENGTH - (end - start);
            }
            
        }
        return (maxCharactersInMessage - messageLength);
    }
    
    /**
     * In order to comply with Twitter's "Developer Display Requirements" 
     *   https://dev.twitter.com/terms/display-requirements
     * @param resId
     * @return Id of alternative (proprietary) term/phrase
     */
    public int alternativeTermForResourceId(int resId) {
        int resId_out = resId;
        if (getId() == ORIGIN_ID_TWITTER) {
            switch (resId) {
                case R.string.button_create_message:
                    resId_out = R.string.button_create_message_twitter;
                    break;
                case R.string.menu_item_destroy_reblog:
                    resId_out = R.string.menu_item_destroy_reblog_twitter;
                    break;
                case R.string.menu_item_reblog:
                    resId_out = R.string.menu_item_reblog_twitter;
                    break;
                case R.string.message:
                    resId_out = R.string.message_twitter;
                    break;
                case R.string.reblogged_by:
                    resId_out = R.string.reblogged_by_twitter;
                    break;
            }
        }
        return resId_out;
    }
    
    /**
     * @param userName Username in the Originating system
     * @param messageOid {@link MyDatabase.Msg#MSG_OID}
     * @return URL
     */
    public String messagePermalink(String userName, String messageOid) {
        String url = "";
        if (getId() == ORIGIN_ID_TWITTER) {
            url = "https://twitter.com/"
                    + userName 
                    + "/status/"
                    + messageOid;
        } else if (getId() == ORIGIN_ID_PUMPIO) {
            url = messageOid; 
        } 
        
        return url;
    }

    public OriginConnectionData getConnectionData() {
        return connectionData;
    }
}
