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

import org.andstatus.app.net.Connection;
import org.andstatus.app.net.ConnectionBasicAuth;
import org.andstatus.app.net.ConnectionOAuth;

/**
 *  Originating (source) Microblogging system (twitter.com, identi.ca, ... ) where messages are being created. 
 *  TODO: Currently the class is almost a stub and serves for ONE origin only :-)
 * @author yvolk
 *
 */
public class Origin {
    private static final String TAG = Origin.class.getSimpleName();

    /**
     * API used by the Originating system
     */
    public enum OriginApiEnum {
        TWITTER,
        IDENTICA
    }
    
    /**
     * Default value for the Originating system mId.
     * TODO: Create a table of these "Origins" ?!
     */
    public static long ORIGIN_ID_DEFAULT = 1;
    /**
     * Predefined ID for default Status.net system 
     * <a href="http://status.net/wiki/Twitter-compatible_API">identi.ca API</a>
     */
    public static long ORIGIN_ID_IDENTICA = 2;
    /**
     * Name of the default Originating system (it is unique and permanent as an ID).
     */
    public static String ORIGIN_NAME_DEFAULT = "Twitter";
    public static String ORIGIN_NAME_IDENTICA = "identi.ca";

    /**
     * Maximum number of characters in the message
     */
    private static int CHARS_MAX = 140;
    /**
     * Length of the link after changing to the shortened link
     * -1 means that length doesn't change
     * For Twitter.com see https://dev.twitter.com/docs/api/1/get/help/configuration
     */
    private static int LINK_LENGTH = 21;
    
    private String mName = "";
    private long mId = 0;
    private OriginApiEnum mApi;

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

    public OriginApiEnum getApi() {
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
     * SharedPreferences - These preferences are per User
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
                    mConnection = new ConnectionOAuth(ma);
                } else {
                    mConnection = new ConnectionBasicAuth(ma);
                }
            }
        }
        return mConnection;
    }

    private Origin(String name) {
        mName = name;
        // TODO: Persistence for Origins
        if (this.mName.compareToIgnoreCase(ORIGIN_NAME_DEFAULT) == 0) {
            mApi = OriginApiEnum.TWITTER;
            mId = ORIGIN_ID_DEFAULT;
            mOAuth = true;
            mCanChangeOAuth = false;
            mCanSetUsername = false;
            mOauthBaseUrl = "http://api.twitter.com";
            mBaseUrl = mOauthBaseUrl + "/1";
        } else if (this.mName.compareToIgnoreCase(ORIGIN_NAME_IDENTICA) == 0) {
            mApi = OriginApiEnum.IDENTICA;
            mId = ORIGIN_ID_IDENTICA;
            mOAuth = false;   // TODO: Set this to true once OAuth in identi.ca will work
            mCanChangeOAuth = true;
            mCanSetUsername = true;
            mOauthBaseUrl = "https://identi.ca/api";
            mBaseUrl = mOauthBaseUrl;
        }
    }
    
    private Origin(long id) {
        this(id == ORIGIN_ID_DEFAULT ? ORIGIN_NAME_DEFAULT :
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
}
