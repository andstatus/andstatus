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
package org.andstatus.app.account;

import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;

import org.andstatus.app.R;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.net.Connection;
import org.andstatus.app.net.Connection.ApiEnum;
import org.andstatus.app.net.ConnectionBasicAuth;
import org.andstatus.app.net.ConnectionBasicAuthStatusNet;

/**
 *  Originating (source) Microblogging system (twitter.com, identi.ca, ... ) where messages are being created. 
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
     * <a href="http://status.net/wiki/Twitter-compatible_API">identi.ca API</a>
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
    
    private String mName = "";
    private long mId = 0;
    private ApiEnum mApi = ApiEnum.UNKNOWN_API;

    /**
     * Default OAuth setting
     */
    private boolean mOAuth = true;
    /**
     * Can OAuth connection setting can be turned on/off from the default setting
     * Starting from 2010-09 twitter.com allows OAuth only
     */
    private boolean mCanChangeOAuth = false;
    /**
     * Can user set username for the new user manually?
     * This is only for no OAuth
     */
    private boolean mCanSetUsername = false;
    
    /**
     * Base URL for connection to the System
     */
    private String mBaseUrl = "";
    /**
     * Base URL for OAuth related requests to the System
     */
    private String mOauthBaseUrl = "";
    
    private Connection mConnection = null;

    public static Origin getOrigin(String name) {
        return new Origin(name);
    }
    
    public static Origin getOrigin(long id) {
        return new Origin(id);
    }

    public static Origin toExistingOrigin(String originName_in) {
        Origin origin = getOrigin(UserNameUtil.fixUsername(originName_in));
        if (origin.getId() == 0) {
            origin = getOrigin(Origin.ORIGIN_ID_DEFAULT);
        }
        return origin;
    }
    
    /**
     * @return the Origin name, unique in the application
     */
    public String getName() {
        return mName;
    }

    /**
     * @return the OriginId in MyDatabase. 0 means that this system doesn't exist
     */
    public long getId() {
        return mId;
    }

    public ApiEnum getApi() {
        return mApi;
    }

    /**
     * @return Base URL for connection to the System
     */
    public String getBaseUrl() {
        return mBaseUrl;
    }

    /**
     * @return Base URL for OAuth related requests to the System
     */
    public String getOauthBaseUrl() {
        return mOauthBaseUrl;
    }

    /**
     * Was this Origin stored for future reuse?
     */
    public boolean isPersistent() {
        return (getId() != 0);
    }
    
    /**
     * @return Default OAuth setting
     */
    public boolean isOAuth() {
        return mOAuth;
    }

    /**
     * @return the Can OAuth connection setting can be turned on/off from the default setting
     */
    public boolean canChangeOAuth() {
        return mCanChangeOAuth;
    }

    /**
     * @return Can app user set username for the new "Origin user" manually?
     */
    public boolean canSetUsername(boolean isOauthUser) {
        boolean can = false;
        if (mCanSetUsername) {
            if (!isOauthUser) {
                can = true;
            }
        }
        return can;
    }

    /**
     * Connection is per User
     */
     public Connection getConnection(MyAccount ma, boolean oauth) {
        if (mConnection != null) {
            if (mConnection.isOAuth() != oauth) {
                mConnection = null;
            }
        }
        if (mConnection == null) {
            if (ma == null) {
                Log.e(TAG, "MyAccount is null ??" );
            } else {
                if (oauth) {
                    switch (mApi) {
                        case TWITTER1P0:
                            mConnection = new org.andstatus.app.net.ConnectionOAuth1p0(ma, mApi, getBaseUrl(), getOauthBaseUrl());
                            break;
                        default:
                            mConnection = new org.andstatus.app.net.ConnectionOAuth1p1(ma, mApi, getBaseUrl(), getOauthBaseUrl());
                    }
                } else {
                    switch (mApi) {
                        case STATUSNET_TWITTER:
                            mConnection = new ConnectionBasicAuthStatusNet(ma, mApi, getBaseUrl());
                            break;
                        default:
                            mConnection = new ConnectionBasicAuth(ma, mApi, getBaseUrl());
                    }
                }
            }
        }
        return mConnection;
    }

    private Origin(String name) {
        mName = name;
        // TODO: Persistence for Origins
        if (this.mName.compareToIgnoreCase(ORIGIN_NAME_TWITTER) == 0) {
            mId = ORIGIN_ID_TWITTER;
            mApi = ApiEnum.TWITTER1P1;
            mOAuth = true;
            mCanChangeOAuth = false;
            mCanSetUsername = false;
            mOauthBaseUrl = "http://api.twitter.com";
            switch (mApi) {
                case TWITTER1P0:
                    mBaseUrl = mOauthBaseUrl + "/1";
                    break;
                default:
                    mBaseUrl = mOauthBaseUrl + "/1.1";
            }
        } else if (this.mName.compareToIgnoreCase(ORIGIN_NAME_IDENTICA) == 0) {
            mId = ORIGIN_ID_IDENTICA;
            mApi = ApiEnum.STATUSNET_TWITTER;
            mOAuth = false;   // TODO: Set this to true once OAuth in identi.ca will work
            mCanChangeOAuth = false;
            mCanSetUsername = true;
            mOauthBaseUrl = "https://identi.ca/api";
            mBaseUrl = mOauthBaseUrl;
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
    public int messageCharactersLeft(String message) {
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
