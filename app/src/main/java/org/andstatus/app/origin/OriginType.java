/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.lang.SelectableEnum;
import org.andstatus.app.net.http.HttpConnectionBasic;
import org.andstatus.app.net.http.HttpConnectionEmpty;
import org.andstatus.app.net.http.HttpConnectionOAuthApache;
import org.andstatus.app.net.http.HttpConnectionOAuthJavaNet;
import org.andstatus.app.net.http.HttpConnectionOAuthMastodon;
import org.andstatus.app.net.social.ConnectionEmpty;
import org.andstatus.app.net.social.ConnectionMastodon;
import org.andstatus.app.net.social.ConnectionTheTwitter;
import org.andstatus.app.net.social.ConnectionTwitterGnuSocial;
import org.andstatus.app.net.social.Patterns;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;
import java.util.function.Function;
import java.util.regex.Pattern;

public enum OriginType implements SelectableEnum {
    /**
     * Origin type for Twitter system 
     * <a href="https://dev.twitter.com/docs">Twitter Developers' documentation</a>
     */
    TWITTER(1, "Twitter", ApiEnum.TWITTER1P1, NoteName.NO, PublicWithAudience.NO),
    /**
     * Origin type for the pump.io system 
     * Till July of 2013 (and v.1.16 of AndStatus) the API was: 
     * <a href="http://status.net/wiki/Twitter-compatible_API">Twitter-compatible identi.ca API</a>
     * Since July 2013 the API is <a href="https://github.com/e14n/pump.io/blob/master/API.md">pump.io API</a>
     */
    PUMPIO(2, "Pump.io", ApiEnum.PUMPIO, NoteName.YES, PublicWithAudience.YES),
    GNUSOCIAL(3, "GNU social", ApiEnum.GNUSOCIAL_TWITTER, NoteName.NO, PublicWithAudience.NO),
    /** <a href="https://github.com/Gargron/mastodon">Mastodon at GitHub</a> */
    MASTODON(4, "Mastodon", ApiEnum.MASTODON, NoteName.YES, PublicWithAudience.NO),
    UNKNOWN(0, "?", ApiEnum.UNKNOWN_API, NoteName.NO, PublicWithAudience.NO);

    private enum NoteName { YES, NO}
    private enum PublicWithAudience { YES, NO}

    /**
     * Connection APIs known
     */
    private enum ApiEnum {
        UNKNOWN_API,
        /** Twitter API v.1 https://dev.twitter.com/docs/api/1     */
        TWITTER1P0,
        /** Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1 */
        TWITTER1P1,
        /** GNU social (former: Status Net) Twitter compatible API http://status.net/wiki/Twitter-compatible_API  */
        GNUSOCIAL_TWITTER,
        /** https://github.com/e14n/pump.io/blob/master/API.md */
        PUMPIO,
        /** https://github.com/Gargron/mastodon/wiki/API */
        MASTODON
    }

    private static final String BASIC_PATH_DEFAULT = "api";
    private static final String OAUTH_PATH_DEFAULT = "oauth";
    private static final String USERNAME_EXAMPLES_SIMPLE = "AndStatus user357 peter";
    public static final OriginType ORIGIN_TYPE_DEFAULT = TWITTER;
    public static final int TEXT_LIMIT_MAXIMUM = 5000;

    private final long id;
    private final String title;

    protected final boolean canSetUrlOfOrigin;

    public final Function<MyContext, Origin> originFactory;
    private final Class<? extends org.andstatus.app.net.social.Connection> connectionClass;
    private final Class<? extends org.andstatus.app.net.http.HttpConnection> httpConnectionClassOauth;
    private final Class<? extends org.andstatus.app.net.http.HttpConnection> httpConnectionClassBasic;

    private final boolean allowEditing;

    /** Default OAuth setting */
    protected boolean isOAuthDefault = true;
    /** Can OAuth connection setting can be turned on/off from the default setting */
    protected boolean canChangeOAuth = false;

    protected boolean shouldSetNewUsernameManuallyIfOAuth = false;

    /** May a User set username for the new Account/Actor manually?
     * This is only for no OAuth */
    protected boolean shouldSetNewUsernameManuallyNoOAuth = false;
    protected final Pattern usernameRegExPattern;
    public final String validUsernameExamples;
    /**
     * Length of the link after changing to the shortened link
     * 0 means that length doesn't change
     * For Twitter.com see <a href="https://dev.twitter.com/docs/api/1.1/get/help/configuration">GET help/configuration</a>
     */
    protected int shortUrlLengthDefault = 0;
    
    protected boolean sslDefault = true;
    protected boolean canChangeSsl = false;

    protected boolean allowHtmlDefault = true;
    /** Maximum number of characters in a note */
    protected int textLimitDefault = 0;
    private URL urlDefault = null;
    private String basicPath = BASIC_PATH_DEFAULT;
    private String oauthPath = OAUTH_PATH_DEFAULT;
    private final boolean mAllowAttachmentForPrivateNote;

    private boolean isPublicTimeLineSyncable = false;
    private boolean isSearchTimelineSyncable = true;
    private boolean isPrivateTimelineSyncable = true;
    private boolean isInteractionsTimelineSyncable = true;
    private final boolean isPrivateNoteAllowsReply;
    private final boolean isSelectable;
    public final boolean hasNoteName;
    public final boolean isPublicWithAudienceAllowed;

    OriginType(long id, String title, ApiEnum api, NoteName noteName, PublicWithAudience publicWithAudience) {
        this.id = id;
        this.title = title;
        hasNoteName = noteName == NoteName.YES;
        isPublicWithAudienceAllowed = publicWithAudience == PublicWithAudience.YES;

        switch (api) {
            case TWITTER1P1:
                isOAuthDefault = true;
                // Starting from 2010-09 twitter.com allows OAuth only
                canChangeOAuth = false;  
                canSetUrlOfOrigin = true;
                shouldSetNewUsernameManuallyIfOAuth = false;
                shouldSetNewUsernameManuallyNoOAuth = true;
                // TODO: Read from Config
                shortUrlLengthDefault = 23; 
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN;
                validUsernameExamples = USERNAME_EXAMPLES_SIMPLE;
                textLimitDefault = 280;
                urlDefault = UrlUtils.fromString("https://api.twitter.com");
                basicPath = "1.1";
                oauthPath = OAUTH_PATH_DEFAULT;
                originFactory = myContext -> new OriginTwitter(myContext, this);
                connectionClass = ConnectionTheTwitter.class;
                httpConnectionClassOauth = HttpConnectionOAuthApache.class;
                httpConnectionClassBasic = HttpConnectionBasic.class;
                mAllowAttachmentForPrivateNote = false;
                allowEditing = false;
                isPrivateNoteAllowsReply = false;
                isSelectable = true;
                break;
            case PUMPIO:
                isOAuthDefault = true;  
                canChangeOAuth = false;
                canSetUrlOfOrigin = false;
                shouldSetNewUsernameManuallyIfOAuth = true;
                shouldSetNewUsernameManuallyNoOAuth = false;
                usernameRegExPattern = Patterns.WEBFINGER_ID_REGEX_PATTERN;
                validUsernameExamples = "andstatus@identi.ca test425@1realtime.net";
                // This is not a hard limit, just for convenience
                textLimitDefault = TEXT_LIMIT_MAXIMUM;
                basicPath = BASIC_PATH_DEFAULT;
                oauthPath = OAUTH_PATH_DEFAULT;
                originFactory = myContext -> new OriginPumpio(myContext, this);
                connectionClass = ConnectionPumpio.class;
                httpConnectionClassOauth = HttpConnectionOAuthJavaNet.class;
                httpConnectionClassBasic = HttpConnectionEmpty.class;
                mAllowAttachmentForPrivateNote = true;
                isSearchTimelineSyncable = false;
                isPrivateTimelineSyncable = false;
                isInteractionsTimelineSyncable = false;
                allowEditing = true;
                isPrivateNoteAllowsReply = true;
                isSelectable = true;
                break;
            case GNUSOCIAL_TWITTER:
                isOAuthDefault = false;  
                canChangeOAuth = false; 
                canSetUrlOfOrigin = true;
                shouldSetNewUsernameManuallyIfOAuth = false;
                shouldSetNewUsernameManuallyNoOAuth = true;
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN;
                validUsernameExamples = USERNAME_EXAMPLES_SIMPLE;
                canChangeSsl = true;
                basicPath = BASIC_PATH_DEFAULT;
                oauthPath = BASIC_PATH_DEFAULT;
                originFactory = myContext -> new OriginGnuSocial(myContext, this);
                connectionClass = ConnectionTwitterGnuSocial.class;
                httpConnectionClassOauth = HttpConnectionOAuthApache.class;
                httpConnectionClassBasic = HttpConnectionBasic.class;
                mAllowAttachmentForPrivateNote = false;
                isPublicTimeLineSyncable = true;
                allowEditing = false;
                isPrivateNoteAllowsReply = false;
                isSelectable = true;
                break;
            case MASTODON:
                isOAuthDefault = true;
                canChangeOAuth = false;
                canSetUrlOfOrigin = true;
                shouldSetNewUsernameManuallyIfOAuth = false;
                shouldSetNewUsernameManuallyNoOAuth = true;
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN;
                validUsernameExamples = USERNAME_EXAMPLES_SIMPLE;
                textLimitDefault = 500;
                basicPath = "api/v1";
                oauthPath = "oauth";
                originFactory = myContext -> new OriginMastodon(myContext, this);
                connectionClass = ConnectionMastodon.class;
                httpConnectionClassOauth = HttpConnectionOAuthMastodon.class;
                httpConnectionClassBasic = HttpConnectionEmpty.class;
                mAllowAttachmentForPrivateNote = false;
                isSearchTimelineSyncable = true;
                isPrivateTimelineSyncable = false;
                isPublicTimeLineSyncable = true;
                allowEditing = false;
                isPrivateNoteAllowsReply = false;
                isSelectable = true;
                break;
            default:
                canSetUrlOfOrigin = false;
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN;
                originFactory = myContext -> new Origin(myContext, this);
                connectionClass = ConnectionEmpty.class;
                httpConnectionClassOauth = HttpConnectionEmpty.class;
                httpConnectionClassBasic = HttpConnectionEmpty.class;
                mAllowAttachmentForPrivateNote = false;
                allowEditing = false;
                isPrivateNoteAllowsReply = false;
                validUsernameExamples = USERNAME_EXAMPLES_SIMPLE;
                isSelectable = false;
                break;
        }
    }

    public Class<? extends org.andstatus.app.net.social.Connection> getConnectionClass() {
        return connectionClass;
    }
    
    public Class<? extends org.andstatus.app.net.http.HttpConnection> getHttpConnectionClass(boolean isOAuth) {
        if (fixIsOAuth(isOAuth)) {
            return httpConnectionClassOauth;
        } else {
            return httpConnectionClassBasic;
        }
    }

    @Override
    public boolean isSelectable() {
        return isSelectable;
    }

    @Override
    public String getCode() {
        return Long.toString(getId());
    }

    @Override
    public CharSequence title(Context context) {
        return getTitle();
    }

    @Override
    public int getDialogTitleResId() {
        return R.string.label_origin_type;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean canSetUrlOfOrigin() {
        return canSetUrlOfOrigin;
    }
    
    @Override
    public String toString() {
        return "OriginType: {id:" + id + ", title:'" + title + "'}";
    }
    
    public boolean fixIsOAuth(TriState triStateOAuth) {
        return fixIsOAuth(triStateOAuth.toBoolean(isOAuthDefault));
    }

    public boolean fixIsOAuth(boolean isOAuthIn) {
        boolean fixed = isOAuthIn;
        if (fixed != isOAuthDefault && !canChangeOAuth) {
            fixed = isOAuthDefault;
        }
        return fixed;
    }
    
    public boolean allowAttachmentForPrivateNote() {
        return mAllowAttachmentForPrivateNote;
    }
    
    public static OriginType fromId( long id) {
        for(OriginType val : values()) {
            if (val.id == id) {
                return val;
            }
        }
        return UNKNOWN;
    }

    public static OriginType fromCode(String code) {
        for(OriginType val : values()) {
            if (val.getCode().equalsIgnoreCase(code)) {
                return val;
            }
        }
        return UNKNOWN;
    }

    public boolean isTimelineTypeSyncable(TimelineType timelineType) {
        if (timelineType == null || !timelineType.isSyncable()) {
            return false;
        }
        switch(timelineType) {
            case PUBLIC:
                return isPublicTimeLineSyncable;
            case SEARCH:
                return isSearchTimelineSyncable;
            case INTERACTIONS:
            case UNREAD_NOTIFICATIONS:
            case NOTIFICATIONS:
                return isInteractionsTimelineSyncable;
            case PRIVATE:
                return isPrivateTimelineSyncable;
            default:
                return true;
        }
    }

    public boolean isPublicTimeLineSyncable() {
        return isPublicTimeLineSyncable;
    }

    public boolean isSearchTimelineSyncable() {
        return isSearchTimelineSyncable;
    }

    public String getBasicPath() {
        return basicPath;
    }

    public String getOauthPath() {
        return oauthPath;
    }

    public boolean allowEditing() {
        return allowEditing;
    }

    public boolean isPrivateNoteAllowsReply() {
        return isPrivateNoteAllowsReply;
    }

    public boolean isUsernameNeededToStartAddingNewAccount(boolean isOAuth) {
        return isOAuth ? shouldSetNewUsernameManuallyIfOAuth : shouldSetNewUsernameManuallyNoOAuth;
    }

    public URL getUrlDefault() {
        return urlDefault;
    }

    public TextMediaType textMediaTypeToPost() {
        return this == PUMPIO ? TextMediaType.HTML : TextMediaType.PLAIN;
    }
}