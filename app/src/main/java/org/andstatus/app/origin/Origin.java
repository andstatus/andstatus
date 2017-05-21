/**
 * Copyright (C) 2014-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.OriginTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.net.social.MbConfig;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;

/**
 * Microblogging system (twitter.com, identi.ca, ... ) where messages are being
 * created (it's the "Origin" of the messages)
 * 
 * @author yvolk@yurivolkov.com
 */
public class Origin {
    public static final int TEXT_LIMIT_FOR_WEBFINGER_ID = 200;

    private static final String TAG = Origin.class.getSimpleName();

    /** See {@link OriginType#shortUrlLengthDefault} */
    protected int shortUrlLength = 0;

    private OriginType originType = OriginType.UNKNOWN;

    @NonNull
    protected String name = "";
    public static final String KEY_ORIGIN_NAME = "origin_name";

    protected long id = 0;

    protected URL url = null;

    protected boolean ssl = true;
    private SslModeEnum sslMode = SslModeEnum.SECURE;

    private TriState mUseLegacyHttpProtocol = TriState.UNKNOWN;

    private boolean allowHtml = false;

    /**
     * Maximum number of characters in the message
     */
    private int textLimit = OriginType.TEXT_LIMIT_MAXIMUM;

    
    /** Include this system in Global Search while in Combined Timeline */
    private boolean inCombinedGlobalSearch = false;
    private boolean inCombinedPublicReload = false;
    
    private TriState mMentionAsWebFingerId = TriState.UNKNOWN;
    
    protected Origin() {
        // Empty
    }
    
    public OriginType getOriginType() {
        return originType;
    }

    /**
     * @return the Origin name, unique in the application
     */
    public String getName() {
        return name;
    }

    /**
     * @return the OriginId in MyDatabase. 0 means that this system doesn't
     *         exist
     */
    public long getId() {
        return id;
    }

    /**
     * Was this Origin stored for future reuse?
     */
    public boolean isPersistent() {
        return getId() != 0;
    }

    public boolean isValid() {
        return originType != OriginType.UNKNOWN
                && isNameValid()
                && urlIsValid()
                && (isSsl() == originType.sslDefault || originType.canChangeSsl);
    }

    public boolean isOAuthDefault() {
        return originType.isOAuthDefault;
    }

    /**
     * @return the Can OAuth connection setting can be turned on/off from the
     *         default setting
     */
    public boolean canChangeOAuth() {
        return originType.canChangeOAuth;
    }

    public boolean isUsernameValid(String username) {
        boolean ok = false;
        if (!TextUtils.isEmpty(username)) {
            ok = username.matches(originType.usernameRegEx);
        }
        return ok;
    }

    /**
     * Calculates number of Characters left for this message taking shortened
     * URL's length into account.
     * 
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
                for (int ind1 = 0; ind1 < nLinks; ind1++) {
                    int start = ss.getSpanStart(spans[ind1]);
                    int end = ss.getSpanEnd(spans[ind1]);
                    messageLength += shortUrlLength - (end - start);
                }
            }
        }
        return textLimit - messageLength;
    }

    public int alternativeTermForResourceId(@StringRes int resId) {
        return resId;
    }

    public String messagePermalink(long messageId) {
        return "";
    }

    public boolean canSetUrlOfOrigin() {
        return originType.canSetUrlOfOrigin();
    }

    public boolean isNameValid() {
        return isNameValid(name);
    }

    public boolean isNameValid(String originNameToCheck) {
        boolean ok = false;
        if (originNameToCheck != null) {
            String validOriginNameRegex = "[a-zñA-Z_0-9/\\.\\-]+";
            ok = originNameToCheck.matches(validOriginNameRegex);
        }
        return ok;
    }

    public URL getUrl() {
        return url;
    }

    public Uri fixUriforPermalink(Uri uri1) {
        return uri1;
    }
    
    public boolean urlIsValid() {
        if (originType.canSetUrlOfOrigin()) {
            return url != null;
        } else {
            return true;
        }
    }

    public boolean isSsl() {
        return ssl;
    }

    public SslModeEnum getSslMode() {
        return sslMode;
    }

    public TriState useLegacyHttpProtocol() {
        return mUseLegacyHttpProtocol;
    }
    
    public boolean isHtmlContentAllowed() {
        return allowHtml;
    }

    public boolean isSyncedForAllOrigins(boolean isSearch) {
        if (isSearch) {
            if (isInCombinedGlobalSearch()) {
                return true;
            }
        } else {
            if (isInCombinedPublicReload()) {
                return true;
            }
        }
        return false;
    }

    public void setInCombinedGlobalSearch(boolean inCombinedGlobalSearch) {
        if (originType.isSearchTimelineSyncable()) {
            this.inCombinedGlobalSearch = inCombinedGlobalSearch;
        }
    }

    public boolean isInCombinedGlobalSearch() {
        return inCombinedGlobalSearch;
    }

    public boolean isInCombinedPublicReload() {
        return inCombinedPublicReload;
    }

    private boolean isMentionAsWebFingerIdDefault() {
        switch (originType) {
            case PUMPIO:
                return true;
            case TWITTER:
                return false;
            default:
                break;
        }
        return getTextLimit() == 0 || getTextLimit() >= TEXT_LIMIT_FOR_WEBFINGER_ID;
    }
    
    public TriState getMentionAsWebFingerId() {
        return mMentionAsWebFingerId;
    }

    public boolean isMentionAsWebFingerId() {
        return mMentionAsWebFingerId.toBoolean(isMentionAsWebFingerIdDefault());
    }
    
    public boolean hasChildren() {
        long count = 0;
        Cursor cursor = null;
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, "hasChildren; Database is null");
            return false;
        }
        try {
            String sql = "SELECT Count(*) FROM " + MsgTable.TABLE_NAME + " WHERE "
                    + MsgTable.ORIGIN_ID + "=" + id;
            cursor = db.rawQuery(sql, null);
            if (cursor.moveToNext()) {
                count = cursor.getLong(0);
            }
            cursor.close();
            if (count == 0) {
                sql = "SELECT Count(*) FROM " + UserTable.TABLE_NAME + " WHERE "
                        + UserTable.ORIGIN_ID + "=" + id;
                cursor = db.rawQuery(sql, null);
                if (cursor.moveToNext()) {
                    count = cursor.getLong(0);
                }
                cursor.close();
            }
            MyLog.v(this, this.toString() + " has " + count + " children");
        } catch (Exception e) {
            MyLog.e(this, "Error counting children", e);
        } finally {
            DbUtils.closeSilently(cursor);
        }
        return count != 0;
    }

    public static Origin getEmpty() {
        return getEmpty(OriginType.UNKNOWN);
    }

    private static Origin getEmpty(OriginType originType) {
        Origin origin;
        try {
            origin = originType.getOriginClass().newInstance();
            origin.originType = originType;
        } catch (Exception e) {
            MyLog.e(TAG, originType.getTitle(), e);
            origin = new Origin();
            origin.originType = OriginType.UNKNOWN;
        }
        origin.url = origin.originType.urlDefault;
        origin.ssl = origin.originType.sslDefault;
        origin.allowHtml = origin.originType.allowHtmlDefault;
        origin.shortUrlLength = origin.originType.shortUrlLengthDefault;
        origin.textLimit = origin.originType.textLimitDefault;
        origin.setInCombinedGlobalSearch(true);
        origin.setInCombinedPublicReload(true);
        return origin;
    }

    @Override
    public String toString() {
        return "Origin:{" + (isValid() ? "" : "(invalid) ") + "name:" + getName()
                + ", type:" + originType
                + (getUrl() != null ? ", url:" + getUrl() : "" )
                + (isSsl() ? ", " + getSslMode() : "" )
                + (getMentionAsWebFingerId() != TriState.UNKNOWN ? ", mentionAsWf:" + getMentionAsWebFingerId() : "" )
                + "}";
    }

    protected int getTextLimit() {
        return textLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Origin)) return false;

        Origin origin = (Origin) o;

        if (id != origin.id) return false;
        return StringUtils.equalsNotEmpty(name, origin.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (int) (id ^ (id >>> 32));
        return result;
    }

    public void setInCombinedPublicReload(boolean inCombinedPublicReload) {
        if (originType.isPublicTimeLineSyncable()) {
            this.inCombinedPublicReload = inCombinedPublicReload;
        }
    }

    public static final class Builder {

        private final Origin origin;
        /*
         * Result of the last "save" action
         */
        private boolean saved = false;

        public boolean isSaved() {
            return saved;
        }

        public Builder(OriginType originType) {
            origin = getEmpty(originType);
        }

        /**
         * Loading persistent Origin
         */
        public Builder(Cursor cursor) {
            OriginType originType1 = OriginType.fromId(
                    DbUtils.getLong(cursor, OriginTable.ORIGIN_TYPE_ID));
            origin = getEmpty(originType1);
            origin.id = DbUtils.getLong(cursor, OriginTable._ID);
            origin.name = DbUtils.getString(cursor, OriginTable.ORIGIN_NAME);
            setHostOrUrl(DbUtils.getString(cursor, OriginTable.ORIGIN_URL));
            setSsl(DbUtils.getBoolean(cursor, OriginTable.SSL));
            setSslMode(SslModeEnum.fromId(DbUtils.getLong(cursor, OriginTable.SSL_MODE)));
            
            origin.allowHtml = DbUtils.getBoolean(cursor, OriginTable.ALLOW_HTML);
            if (originType1.shortUrlLengthDefault == 0) {
                origin.shortUrlLength = DbUtils.getInt(cursor, OriginTable.SHORT_URL_LENGTH);
            }
            if (originType1.textLimitDefault == 0) {
                setTextLimit(DbUtils.getInt(cursor, OriginTable.TEXT_LIMIT));
            }
            origin.setInCombinedGlobalSearch(DbUtils.getBoolean(cursor,
                    OriginTable.IN_COMBINED_GLOBAL_SEARCH));
            origin.setInCombinedPublicReload(DbUtils.getBoolean(cursor,
                    OriginTable.IN_COMBINED_PUBLIC_RELOAD));
            setMentionAsWebFingerId(TriState.fromId(DbUtils.getLong(cursor,
                    OriginTable.MENTION_AS_WEBFINGER_ID)));
            setUseLegacyHttpProtocol(TriState.fromId(DbUtils.getLong(cursor,
                    OriginTable.USE_LEGACY_HTTP)));
        }

        protected void setTextLimit(int textLimit) {
            if (textLimit <= 0) {
                origin.textLimit = OriginType.TEXT_LIMIT_MAXIMUM;
            } else {
                origin.textLimit = textLimit;
            }
        }
        
        public Builder(Origin original) {
            origin = getEmpty(original.originType);
            origin.id = original.id;
            origin.name = original.name;
            setUrl(original.url);
            setSsl(original.ssl);
            setSslMode(original.sslMode);
            setHtmlContentAllowed(original.allowHtml);
            origin.shortUrlLength = original.shortUrlLength;
            setTextLimit(original.getTextLimit());
            setInCombinedGlobalSearch(original.inCombinedGlobalSearch);
            setInCombinedPublicReload(original.inCombinedPublicReload);
            setMentionAsWebFingerId(original.mMentionAsWebFingerId);
            origin.mUseLegacyHttpProtocol = original.mUseLegacyHttpProtocol;
        }

        public Origin build() {
            return new Builder(origin).origin;
        }

        public Builder setName(String nameIn) {
            String name = correctedName(nameIn);
            if (!origin.isPersistent() && origin.isNameValid(name)) {
                origin.name = name;
            }
            return this;
        }

        @NonNull
        private String correctedName(String nameIn) {
            if (origin.isNameValid(nameIn)) {
                return nameIn;
            }
            if (TextUtils.isEmpty(nameIn)) {
                return "";
            }
            // Test with: http://www.regexplanet.com/advanced/java/index.html
            return nameIn.trim().replaceAll("ñ","n")
                    .replaceAll("[^a-zA-Z_0-9/\\.\\-]+", ".").replaceAll("[\\.]+", ".");
        }

        public Builder setUrl(URL urlIn) {
           return urlIn == null ? this : setHostOrUrl(urlIn.toExternalForm());
        }
        
        public Builder setHostOrUrl(String hostOrUrl) {
            if (origin.originType.canSetUrlOfOrigin()) {
               URL url1 = UrlUtils.buildUrl(hostOrUrl, origin.isSsl());
               if (url1 != null) {
                   if (!UrlUtils.isHostOnly(url1) && !url1.toExternalForm().endsWith("/")) {
                       url1 = UrlUtils.fromString(url1.toExternalForm() + "/");
                   }
                   origin.url = url1;
               }
            }
            return this;
        }
        
        public Builder setSsl(boolean ssl) {
            if (origin.originType.canChangeSsl) {
                origin.ssl = ssl;
                setUrl(origin.getUrl());
            }
            return this;
        }

        public Builder setSslMode(SslModeEnum mode) {
            origin.sslMode = mode;
            return this;
        }
        
        public Builder setHtmlContentAllowed(boolean allowHtml) {
            origin.allowHtml = allowHtml;
            return this;
        }

        public Builder setInCombinedGlobalSearch(boolean inCombinedGlobalSearch) {
            origin.setInCombinedGlobalSearch(inCombinedGlobalSearch);
            return this;
        }

        public Builder setInCombinedPublicReload(boolean inCombinedPublicReload) {
            origin.setInCombinedPublicReload(inCombinedPublicReload);
            return this;
        }

        public Builder setMentionAsWebFingerId(TriState mentionAsWebFingerId) {
            origin.mMentionAsWebFingerId = mentionAsWebFingerId;
            return this;
        }

        public Builder setUseLegacyHttpProtocol(TriState useLegacyHttpProtocol) {
            origin.mUseLegacyHttpProtocol = useLegacyHttpProtocol;
            return this;
        }
        
        public Builder save(MbConfig config) {
            origin.shortUrlLength = config.shortUrlLength;
            setTextLimit(config.textLimit);
            save();
            return this;
        }

        public Builder save() {
            saved = false;
            if (!origin.isValid()) {
                MyLog.v(this, "Is not valid: " + origin.toString());
                return this;
            }
            if (origin.id == 0) {
                Origin existing = MyContextHolder.get().persistentOrigins()
                        .fromName(origin.getName());
                if (existing.isPersistent()) {
                    if (origin.originType != existing.originType) {
                        MyLog.e(this, "Origin with this name and other type already exists " + existing.toString());
                        return this;
                    }
                    origin.id = existing.getId();
                }
            }

            ContentValues values = new ContentValues();
            values.put(OriginTable.ORIGIN_URL, origin.url != null ? origin.url.toExternalForm() : "");
            values.put(OriginTable.SSL, origin.ssl);
            values.put(OriginTable.SSL_MODE, origin.getSslMode().getId());
            values.put(OriginTable.ALLOW_HTML, origin.allowHtml);
            values.put(OriginTable.SHORT_URL_LENGTH, origin.shortUrlLength);
            values.put(OriginTable.TEXT_LIMIT, origin.getTextLimit());
            values.put(OriginTable.IN_COMBINED_GLOBAL_SEARCH, origin.inCombinedGlobalSearch);
            values.put(OriginTable.IN_COMBINED_PUBLIC_RELOAD, origin.inCombinedPublicReload);
            values.put(OriginTable.MENTION_AS_WEBFINGER_ID, origin.mMentionAsWebFingerId.getId());
            values.put(OriginTable.USE_LEGACY_HTTP, origin.useLegacyHttpProtocol().getId());

            boolean changed = false;
            if (origin.id == 0) {
                values.put(OriginTable.ORIGIN_NAME, origin.name);
                values.put(OriginTable.ORIGIN_TYPE_ID, origin.originType.getId());
                origin.id = DbUtils.addRowWithRetry(MyContextHolder.get(), OriginTable.TABLE_NAME, values, 3);
                changed = origin.isPersistent();
            } else {
                changed = (DbUtils.updateRowWithRetry(MyContextHolder.get(), OriginTable.TABLE_NAME, origin.id,
                        values, 3) != 0);
            }
            if (changed && MyContextHolder.get().isReady()) {
                MyPreferences.onPreferencesChanged();
            }
            saved = changed;
            return this;
        }

        public boolean delete() {
            boolean deleted = false;
            if (!origin.hasChildren()) {
                SQLiteDatabase db = MyContextHolder.get().getDatabase();
                if (db == null) {
                    MyLog.v(this, "delete; Database is null");
                    return false;
                }
                try {
                    String sql = "DELETE FROM " + OriginTable.TABLE_NAME + " WHERE "
                            + BaseColumns._ID + "=" + origin.id;
                    db.execSQL(sql);
                    deleted = true;
                } catch (Exception e) {
                    MyLog.e(this, "Error deleting Origin", e);
                }
            }
            return deleted;
        }
        
        @Override
        public String toString() {
            return "Builder" + (isSaved() ? "saved " : " not") + " saved; " + origin.toString();
        }
    }
}
