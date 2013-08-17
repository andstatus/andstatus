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

import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import org.andstatus.app.R;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.net.Connection;
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
     * Predefined ID for default Status.net system 
     * Till July of 2013 (and v.1.16 of AndStatus) the API was: 
     * <a href="http://status.net/wiki/Twitter-compatible_API">Twitter-compatible identi.ca API</a>
     * Since July 2013 the API is <a href="https://github.com/e14n/pump.io/blob/master/API.md">pump.io API</a>
     */
    public static long ORIGIN_ID_IDENTICA = 2;
    /**
     * Name of the default Originating system (it is unique and permanent as an ID).
     */
    public static String ORIGIN_NAME_TWITTER = "Twitter";
    public static String ORIGIN_NAME_IDENTICA = "identi.ca";

    /**
     * Maximum number of characters in the message
     */
    private static int CHARS_MAX = 140;
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
    /**
     * Can user set username for the new user manually?
     * This is only for no OAuth
     */
    private boolean canSetUsername = false;
    
    private OriginConnectionData connectionData = new OriginConnectionData();
    
    private Connection connection = null;

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

    /**
     * @return Can app user set username for the new "Origin user" manually?
     */
    public boolean canSetUsername(boolean isOAuthUser) {
        boolean can = false;
        if (canSetUsername) {
            if (!isOAuthUser) {
                can = true;
            }
        }
        return can;
    }

    public boolean areKeysPresent() {
        return (connectionData.isOAuth ? connectionData.clientKeys.areKeysPresent() : false);
    }

    public boolean isOAuth() {
        return connectionData.isOAuth;
    }
    
    public void setOAuth(boolean isOAuth) {
        if (isOAuth != isOAuthDefault && !canChangeOAuth) {
            throw (new IllegalArgumentException("isOAuth cannot be set to "
                    + Boolean.toString(isOAuth)));
        }
        if (isOAuth != connectionData.isOAuth) {
            connection = null;
        }
        if (connection != null && connectionData.isOAuth && !areKeysPresent()) {
            connection = null;
        }
        if (connection == null) {
            connectionData.clientKeys = null;
            connectionData.isOAuth = isOAuth;
            if (connectionData.isOAuth) {
                connectionData.clientKeys = new OAuthClientKeys(id);
            }
        }
    }
    
    public Connection getConnection() {
        if (connection == null) {
            connection = Connection.fromConnectionData(connectionData);
        }
        return connection;
    }

    public void registerClient() {
        MyLog.v(TAG, "Registering client for " + name);
        Connection connection = Connection.fromConnectionData(connectionData);
        connection.registerClient();
    }

    private Origin(String name_in) {
        name = name_in;
        // TODO: Persistence for Origins
        if (name.compareToIgnoreCase(ORIGIN_NAME_TWITTER) == 0) {
            id = ORIGIN_ID_TWITTER;
            isOAuthDefault = true;
            canChangeOAuth = false;  // Starting from 2010-09 twitter.com allows OAuth only
            canSetUsername = false;

            connectionData.api = ApiEnum.TWITTER1P1;
            connectionData.isHttps = false;
            connectionData.host = "api.twitter.com";
            connectionData.basicPath = "1.1";
            connectionData.oauthPath = "oauth";
        } else if (name.compareToIgnoreCase(ORIGIN_NAME_IDENTICA) == 0) {
            id = ORIGIN_ID_IDENTICA;
            isOAuthDefault = true;  
            canChangeOAuth = false;
            canSetUsername = false;

            connectionData.api = ApiEnum.PUMPIO;
            connectionData.isHttps = true;
            connectionData.host = "identi.ca";
            connectionData.basicPath = "api";
            connectionData.oauthPath = "oauth";
        }
    }
    
    private Origin(long id) {
        this(id == ORIGIN_ID_TWITTER ? ORIGIN_NAME_TWITTER :
                id == ORIGIN_ID_IDENTICA ? ORIGIN_NAME_IDENTICA :
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
        return (CHARS_MAX - messageLength);
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
        } else if (getId() == ORIGIN_ID_IDENTICA) {
            url = "http://identi.ca/"
                    + userName; 
        } 
        
        return url;
    }
}
