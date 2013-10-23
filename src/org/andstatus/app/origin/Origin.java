/**
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
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.net.Connection.ApiEnum;
import org.andstatus.app.net.MbConfig;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

/**
 *  Microblogging system (twitter.com, identi.ca, ... ) where messages are being created
 *  (it's the "Origin" of the messages). 
 *  TODO: Currently the class is almost a stub and serves for several predefined origins only :-)
 * @author yvolk@yurivolkov.com
 *
 */
public class Origin {

    public enum OriginEnum {
        /**
         * Predefined Origin for Twitter system 
         * <a href="https://dev.twitter.com/docs">Twitter Developers' documentation</a>
         */
        TWITTER(1, "twitter", ApiEnum.TWITTER1P1, OriginTwitter.class),
        /**
         * Predefined Origin for the pump.io system 
         * Till July of 2013 (and v.1.16 of AndStatus) the API was: 
         * <a href="http://status.net/wiki/Twitter-compatible_API">Twitter-compatible identi.ca API</a>
         * Since July 2013 the API is <a href="https://github.com/e14n/pump.io/blob/master/API.md">pump.io API</a>
         */
        PUMPIO(2, "pump.io", ApiEnum.PUMPIO, OriginPumpio.class),
        STATUSNET(3, "status.net", ApiEnum.STATUSNET_TWITTER, OriginStatusNet.class),
        UNKNOWN(0,"unknownMbSystem", ApiEnum.UNKNOWN_API, Origin.class);
        
        private long id;
        private String name;
        private ApiEnum api;
        private Class<? extends Origin> originClass;
        
        private OriginEnum(long id, String name, ApiEnum api, Class<? extends Origin> originClass) {
            this.id = id;
            this.name = name;
            this.api = api;
            this.originClass = originClass;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public ApiEnum getApi() {
            return api;
        }

        public Origin newOrigin() {
            Origin origin = null;
            try {
                origin = originClass.newInstance();
                origin.id = getId();
                origin.name = getName();
                origin.api = getApi();
                if (origin.canSetHostOfOrigin()) {
                    origin.host = MyPreferences.getDefaultSharedPreferences().getString(origin.keyOf(KEY_HOST_OF_ORIGIN),"");
                }
                if (origin.canChangeSsl()) {
                    origin.ssl = MyPreferences.getDefaultSharedPreferences().getBoolean(origin.keyOf(KEY_SSL), true);
                }
                origin.shortUrlLength = MyPreferences.getDefaultSharedPreferences().getInt(origin.keyOf(KEY_SHORTURLLENGTH), 0);
                origin.textLimit = MyPreferences.getDefaultSharedPreferences().getInt(origin.keyOf(KEY_TEXT_LIMIT), TEXT_LIMIT_DEFAULT);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return origin;        
        }
        
        @Override
        public String toString() {
            return id + "-" + name;
        }
        
        public static OriginEnum fromId( long id) {
            OriginEnum originEnum = UNKNOWN;
            for(OriginEnum oe : values()) {
                if (oe.id == id) {
                    originEnum = oe;
                    break;
                }
            }
            return originEnum;
        }

        public static OriginEnum fromName( String name) {
            OriginEnum originEnum = UNKNOWN;
            for(OriginEnum oe : values()) {
                if (oe.name.equalsIgnoreCase(name)) {
                    originEnum = oe;
                    break;
                }
            }
            return originEnum;
        }
    }
    
    /**
     * Default Originating system
     * TODO: Create a table of these "Origins" ?!
     */
    public static OriginEnum ORIGIN_ENUM_DEFAULT = OriginEnum.TWITTER;

    /** 
     * The URI is consistent with "scheme" and "host" in AndroidManifest
     * Pump.io doesn't work with this scheme: "andstatus-oauth://andstatus.org"
     */
    public static final Uri CALLBACK_URI = Uri.parse("http://oauth-redirect.andstatus.org");
    
    /**
     * Length of the link after changing to the shortened link
     * 0 means that length doesn't change
     * For Twitter.com see <a href="https://dev.twitter.com/docs/api/1.1/get/help/configuration">GET help/configuration</a>
     */
    protected int shortUrlLength = 0;
    private static final String KEY_SHORTURLLENGTH = "shorturllength";
    
    protected String name = OriginEnum.UNKNOWN.getName();
    protected long id = 0;
    protected ApiEnum api = ApiEnum.UNKNOWN_API;

    /**
     * Default OAuth setting
     */
    protected boolean isOAuthDefault = true;
    /**
     * Can OAuth connection setting can be turned on/off from the default setting
     */
    protected boolean canChangeOAuth = false;
    protected boolean shouldSetNewUsernameManuallyIfOAuth = false;
    /**
     * Can user set username for the new user manually?
     * This is only for no OAuth
     */
    protected boolean shouldSetNewUsernameManuallyNoOAuth = false;
    
    protected String host = "";
    public final static String KEY_HOST_OF_ORIGIN = "host_of_origin"; 
    protected boolean canSetHostOfOrigin = false;

    protected boolean ssl = true;
    public final static String KEY_SSL = "ssl"; 
    protected boolean canChangeSsl = false;
    
    public static int TEXT_LIMIT_DEFAULT = 140;
    public final static String KEY_TEXT_LIMIT = "textlimit"; 
    /**
     * Maximum number of characters in the message
     */
    protected int textLimit = TEXT_LIMIT_DEFAULT;
    protected String usernameRegEx = "[a-zA-Z_0-9/\\.\\-\\(\\)]+";
    
    public static Origin toExistingOrigin(String originName_in) {
        Origin origin = fromOriginName(originName_in);
        if (origin.getId() == 0) {
            origin = fromOriginId(ORIGIN_ENUM_DEFAULT.getId());
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
        return api;
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
            if (!ok && MyLog.isLoggable(this, MyLog.INFO)) {
                MyLog.i(this, "The Username is not valid: \"" + username + "\" in " + name);
            }
        }
        return ok;
    }
    
    public boolean isUsernameValidToStartAddingNewAccount(String username, boolean isOAuthUser) {
        return false;
    }
    
    public static Origin fromOriginId(long id) {
        return OriginEnum.fromId(id).newOrigin();
    }

    public static Origin fromOriginName(String name) {
        return OriginEnum.fromName(name).newOrigin();
    }

    /**
     * Calculates number of Characters left for this message
     * taking shortened URL's length into account.
     * @author yvolk@yurivolkov.com
     */
    public int charactersLeftForMessage(String message) {
        int messageLength = 0;
        if (!TextUtils.isEmpty(message)) {
            messageLength = message.length();
            
            if (shortUrlLength > 0) {
                // Now try to adjust the length taking links into account
                SpannableString ss = SpannableString.valueOf(message);
                Linkify.addLinks(ss, Linkify.WEB_URLS);
                URLSpan[] spans = ss.getSpans(0, messageLength, URLSpan.class);
                long nLinks = spans.length;
                for (int ind1=0; ind1 < nLinks; ind1++) {
                    int start = ss.getSpanStart(spans[ind1]);
                    int end = ss.getSpanEnd(spans[ind1]);
                    messageLength += shortUrlLength - (end - start);
                }
            }
        }
        return (textLimit - messageLength);
    }
    
    public int alternativeTermForResourceId(int resId) {
        return resId;
    }
    
    public String messagePermalink(String userName, long messageId) {
        return "";
    }

    public OriginConnectionData getConnectionData(TriState triState) {
        OriginConnectionData connectionData = new OriginConnectionData();
        connectionData.host = host;
        connectionData.api = api;
        connectionData.originId = id;
        connectionData.isOAuth = triState.toBoolean(isOAuthDefault);
        if (connectionData.isOAuth != isOAuthDefault) {
            if (!canChangeOAuth) {
                connectionData.isOAuth = isOAuthDefault;
            }
        }
        return connectionData;
    }
    
    
    public boolean canSetHostOfOrigin() {
        return canSetHostOfOrigin;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String hostOfOrigin) {    
        if (!canSetHostOfOrigin) { 
            throw new IllegalStateException("The " + name  +" origin doesn' allow setting it's host");
        }
        host = hostOfOrigin;
    }

    public boolean hostIsValid() {
        return hostIsValid(host);
    }

    public boolean hostIsValid(String host) {
        boolean ok = false;
        if (host != null) {
            // From http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address?rq=1
            String validHostnameRegex = "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$";
            ok = host.matches(validHostnameRegex);
        }
        return ok;
    }
    
    public boolean canChangeSsl() {
        return canChangeSsl;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        if (canChangeSsl) {
            this.ssl = ssl;
        }
    }

    public void save(MbConfig config) {
        shortUrlLength = config.shortUrlLength;
        textLimit = config.textLimit;
        save();
    }
    
    public void save() {
        if (canSetHostOfOrigin()) {
            String hostOld = MyPreferences.getDefaultSharedPreferences().getString(keyOf(KEY_HOST_OF_ORIGIN),"");
            if (!hostOld.equals(host)) {
                MyPreferences.getDefaultSharedPreferences().edit().putString(keyOf(KEY_HOST_OF_ORIGIN), host).commit();
            }
        }
        if (canChangeSsl()) {
            boolean sslOld = MyPreferences.getDefaultSharedPreferences().getBoolean(keyOf(KEY_SSL), true);
            if ( sslOld != ssl) {
                MyPreferences.getDefaultSharedPreferences().edit().putBoolean(keyOf(KEY_SSL), ssl).commit();
            }
        }
        int shortUrlLengthOld = MyPreferences.getDefaultSharedPreferences().getInt(keyOf(KEY_SHORTURLLENGTH), 0);
        if ( shortUrlLengthOld != shortUrlLength) {
            MyPreferences.getDefaultSharedPreferences().edit().putInt(keyOf(KEY_SHORTURLLENGTH), shortUrlLength).commit();
        }
        int textLimitOld = MyPreferences.getDefaultSharedPreferences().getInt(keyOf(KEY_TEXT_LIMIT), TEXT_LIMIT_DEFAULT);
        if ( textLimitOld != textLimit) {
            MyPreferences.getDefaultSharedPreferences().edit().putInt(keyOf(KEY_TEXT_LIMIT), textLimit).commit();
        }
    }
    
    private String keyOf(String keyRoot) {
        return keyRoot + Long.toString(id);
    }
}
