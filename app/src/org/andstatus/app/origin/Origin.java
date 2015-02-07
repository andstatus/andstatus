/**
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

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.net.social.Connection.ApiEnum;
import org.andstatus.app.net.social.MbConfig;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;

/**
 * Microblogging system (twitter.com, identi.ca, ... ) where messages are being
 * created (it's the "Origin" of the messages). TODO: Currently the class is
 * almost a stub and serves for several predefined origins only :-)
 * 
 * @author yvolk@yurivolkov.com
 */
public class Origin {
    private static final String TAG = Origin.class.getSimpleName();

    /** See {@link OriginType#shortUrlLengthDefault} */
    protected int shortUrlLength = 0;

    protected OriginType originType = OriginType.UNKNOWN;

    protected String name = "";
    public static final String KEY_ORIGIN_NAME = "origin_name";

    protected long id = 0;

    protected URL url = null;

    protected boolean ssl = true;
    private SslModeEnum sslMode = SslModeEnum.SECURE;

    private boolean allowHtml = false;

    /**
     * Maximum number of characters in the message
     */
    private int textLimit = OriginType.TEXT_LIMIT_MAXIMUM;

    
    /** Include this system in Global Search while in Combined Timeline */
    private boolean inCombinedGlobalSearch = true;
    /** Include this system in Reload while in Combined Public Timeline */
    private boolean inCombinedPublicReload = true;
    
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

    public ApiEnum getApi() {
        return originType.getApi();
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
            if (!ok && MyLog.isLoggable(this, MyLog.INFO)) {
                MyLog.i(this, "The Username is not valid: \"" + username + "\" in " + name);
            }
        }
        return ok;
    }

    public boolean isUsernameNeededToStartAddingNewAccount(boolean isOAuthUser) {
        return !isOAuthUser;
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

    public int alternativeTermForResourceId(int resId) {
        return resId;
    }

    public String messagePermalink(long messageId) {
        return "";
    }

    public OriginConnectionData getConnectionData(TriState triStateOAuth) {
        return OriginConnectionData.fromOrigin(this, triStateOAuth);
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
            String validOriginNameRegex = "[a-zA-Z_0-9/\\.\\-]+";
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

    public boolean canChangeSsl() {
        return originType.canChangeSsl;
    }

    public boolean isSsl() {
        return ssl;
    }

    public SslModeEnum getSslMode() {
        return sslMode;
    }

    String keyOf(String keyRoot) {
        return keyRoot + Long.toString(id);
    }

    public boolean isHtmlContentAllowed() {
        return allowHtml;
    }

    public boolean isInCombinedGlobalSearch() {
        return inCombinedGlobalSearch;
    }

    public boolean isInCombinedPublicReload() {
        return inCombinedPublicReload;
    }
    
    public boolean hasChildren() {
        long count = 0;
        Cursor cursor = null;
        try {
            String sql = "SELECT Count(*) FROM " + MyDatabase.Msg.TABLE_NAME + " WHERE "
                    + MyDatabase.Msg.ORIGIN_ID + "=" + id;
            cursor = MyContextHolder.get().getDatabase().getWritableDatabase().rawQuery(sql, null);
            if (cursor.moveToNext()) {
                count = cursor.getLong(0);
            }
            cursor.close();
            if (count == 0) {
                sql = "SELECT Count(*) FROM " + MyDatabase.User.TABLE_NAME + " WHERE "
                        + MyDatabase.User.ORIGIN_ID + "=" + id;
                cursor = MyContextHolder.get().getDatabase().getWritableDatabase().rawQuery(sql, null);
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
    
    public static Origin getEmpty(OriginType originType) {
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
        return origin;
    }

    @Override
    public String toString() {
        return "Origin:{name:" + getName() + "; url:" + getUrl() 
                + ", " + originType
                + (isSsl() ? ", " + getSslMode() : "" )
                + "}";
    }

    protected int getTextLimit() {
        return textLimit;
    }

    protected void setTextLimit(int textLimit) {
        if (textLimit <= 0) {
            this.textLimit = OriginType.TEXT_LIMIT_MAXIMUM;
        } else {
            this.textLimit = textLimit;
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

        public static Origin getUnknown() {
            return getEmpty(OriginType.UNKNOWN);
        }

        public Builder(OriginType originType) {
            origin = getEmpty(originType);
        }

        /**
         * Loading persistent Origin
         */
        public Builder(Cursor c) {
            OriginType originType1 = OriginType.fromId(c.getLong(c
                    .getColumnIndex(MyDatabase.Origin.ORIGIN_TYPE_ID)));
            origin = getEmpty(originType1);

            origin.id = c.getLong(c.getColumnIndex(MyDatabase.Origin._ID));
            origin.name = c.getString(c.getColumnIndex(MyDatabase.Origin.ORIGIN_NAME));
            setHostOrUrl(c.getString(c.getColumnIndex(MyDatabase.Origin.ORIGIN_URL)));
            setSsl(c.getInt(c.getColumnIndex(MyDatabase.Origin.SSL)) != 0);
            setSslMode(SslModeEnum.fromId(c.getLong(c.getColumnIndex(MyDatabase.Origin.SSL_MODE))));
            
            origin.allowHtml = (c.getInt(c.getColumnIndex(MyDatabase.Origin.ALLOW_HTML)) != 0);
            if (originType1.shortUrlLengthDefault == 0) {
                origin.shortUrlLength = c.getInt(c
                        .getColumnIndex(MyDatabase.Origin.SHORT_URL_LENGTH));
            }
            if (originType1.textLimitDefault == 0) {
                origin.setTextLimit(c.getInt(c.getColumnIndex(MyDatabase.Origin.TEXT_LIMIT)));
            }
            origin.inCombinedGlobalSearch = (c.getInt(c
                    .getColumnIndex(MyDatabase.Origin.IN_COMBINED_GLOBAL_SEARCH)) != 0);
            origin.inCombinedPublicReload = (c.getInt(c
                    .getColumnIndex(MyDatabase.Origin.IN_COMBINED_PUBLIC_RELOAD)) != 0);
        }

        public Builder(Origin original) {
            origin = clone(original);
        }

        private Origin clone(Origin original) {
            Origin cloned = getEmpty(original.originType);
            cloned.id = original.id;
            cloned.name = original.name;
            cloned.url = original.url;
            cloned.ssl = original.ssl;
            cloned.sslMode = original.sslMode;
            cloned.allowHtml = original.allowHtml;
            cloned.shortUrlLength = original.shortUrlLength;
            cloned.setTextLimit(original.getTextLimit());
            cloned.inCombinedGlobalSearch = original.inCombinedGlobalSearch;
            cloned.inCombinedPublicReload = original.inCombinedPublicReload;
            return cloned;
        }

        public Origin build() {
            return clone(origin);
        }

        public Builder setName(String nameIn) {
            String name = correctedName(nameIn);
            if (!origin.isPersistent() && origin.isNameValid(name)) {
                origin.name = name;
            }
            return this;
        }

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
            origin.inCombinedGlobalSearch = inCombinedGlobalSearch;
            return this;
        }

        public Builder setInCombinedPublicReload(boolean inCombinedPublicReload) {
            origin.inCombinedPublicReload = inCombinedPublicReload;
            return this;
        }
        
        public Builder save(MbConfig config) {
            origin.shortUrlLength = config.shortUrlLength;
            origin.setTextLimit(config.textLimit);
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
            values.put(MyDatabase.Origin.ORIGIN_URL, origin.url != null ? origin.url.toExternalForm() : "");
            values.put(MyDatabase.Origin.SSL, origin.ssl);
            values.put(MyDatabase.Origin.SSL_MODE, origin.getSslMode().getId());
            values.put(MyDatabase.Origin.ALLOW_HTML, origin.allowHtml);
            values.put(MyDatabase.Origin.SHORT_URL_LENGTH, origin.shortUrlLength);
            values.put(MyDatabase.Origin.TEXT_LIMIT, origin.getTextLimit());
            values.put(MyDatabase.Origin.IN_COMBINED_GLOBAL_SEARCH, origin.inCombinedGlobalSearch);
            values.put(MyDatabase.Origin.IN_COMBINED_PUBLIC_RELOAD, origin.inCombinedPublicReload);

            boolean changed = false;
            if (origin.id == 0) {
                values.put(MyDatabase.Origin.ORIGIN_NAME, origin.name);
                values.put(MyDatabase.Origin.ORIGIN_TYPE_ID, origin.originType.getId());
                origin.id = DbUtils.addRowWithRetry(MyDatabase.Origin.TABLE_NAME, values, 3);
                changed = origin.isPersistent();
            } else {
                changed = (DbUtils.updateRowWithRetry(MyDatabase.Origin.TABLE_NAME, origin.id,
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
                try {
                    String sql = "DELETE FROM " + MyDatabase.Origin.TABLE_NAME + " WHERE "
                            + BaseColumns._ID + "=" + origin.id;
                    MyContextHolder.get().getDatabase().getWritableDatabase().execSQL(sql);
                    deleted = true;
                } catch (Exception e) {
                    MyLog.e(this, "Error deleting Origin", e);
                }
            }
            return deleted;
        }
    }
}
