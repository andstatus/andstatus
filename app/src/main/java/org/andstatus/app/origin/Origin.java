/*
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
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.andstatus.app.account.AccountName;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.andstatus.app.net.social.Patterns.USERNAME_CHARS;
import static org.junit.Assert.fail;

/**
 * Social network (twitter.com, identi.ca, ... ) where notes are being
 * created (it's the "Origin" of the notes)
 * 
 * @author yvolk@yurivolkov.com
 */
public class Origin implements Comparable<Origin>, IsEmpty {
    static final int TEXT_LIMIT_FOR_WEBFINGER_ID = 200;
    public static final Origin EMPTY = fromType(MyContext.EMPTY, OriginType.UNKNOWN);
    private static final String VALID_NAME_CHARS = "a-zA-Z_0-9/.-";
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("[" + VALID_NAME_CHARS + "]+");
    private static final Pattern INVALID_NAME_PART_PATTERN = Pattern.compile("[^" + VALID_NAME_CHARS + "]+");
    private static final Pattern DOTS_PATTERN = Pattern.compile("[.]+");

    protected final int shortUrlLength;
    public final MyContext myContext;
    private final OriginType originType;

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
     * Maximum number of characters in a note
     */
    private int textLimit = OriginType.TEXT_LIMIT_MAXIMUM;

    
    /** Include this system in Global Search while in Combined Timeline */
    private boolean inCombinedGlobalSearch = false;
    private boolean inCombinedPublicReload = false;
    
    private TriState mMentionAsWebFingerId = TriState.UNKNOWN;
    private boolean isValid = false;

    Origin(MyContext myContext, OriginType originType) {
        this.myContext = myContext;
        this.originType = originType;
        shortUrlLength = originType.shortUrlLengthDefault.value;
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

    @Override
    public boolean isEmpty() {
        return this == EMPTY || originType == OriginType.UNKNOWN;   // TODO avoid second case
    }

    public boolean isValid() {
        return isValid;
    }

    private boolean calcIsValid() {
        return nonEmpty()
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
        return StringUtils.nonEmpty(username) && originType.usernameRegExPattern.matcher(username).matches();
    }

    public long usernameToId(String username) {
        if (StringUtils.isEmpty(username)) return 0;

        String key = getId() + ";" + username;
        Long cachedId = myContext.users().originIdAndUsernameToActorId.get(key);
        if (cachedId != null) return cachedId;
        long storedId = MyQuery.usernameToId(myContext, getId(), username, true);
        if (storedId != 0) {
            myContext.users().originIdAndUsernameToActorId.put(key, storedId);
        }
        return storedId;
    }

    /**
     * Calculates number of Characters left for this note taking shortened
     * URL's length into account.
     * 
     * @author yvolk@yurivolkov.com
     */
    public int charactersLeftForNote(String html) {
        int textLength = 0;
        if (!StringUtils.isEmpty(html)) {
            String textToPost = MyHtml.fromContentStored(html, originType.textMediaTypeToPost);
            textLength = textToPost.length();

            if (shortUrlLength > 0) {
                // Now try to adjust the length taking links into account
                SpannableString ss = SpannableString.valueOf(textToPost);
                Linkify.addLinks(ss, Linkify.WEB_URLS);
                URLSpan[] spans = ss.getSpans(0, textLength, URLSpan.class);
                long nLinks = spans.length;
                for (int ind1 = 0; ind1 < nLinks; ind1++) {
                    int start = ss.getSpanStart(spans[ind1]);
                    int end = ss.getSpanEnd(spans[ind1]);
                    textLength += shortUrlLength - (end - start);
                }
            }
        }
        return textLimit - textLength;
    }

    public int alternativeTermForResourceId(@StringRes int resId) {
        return resId;
    }

    public String getNotePermalink(long noteId) {
        String msgUrl = MyQuery.noteIdToStringColumnValue(NoteTable.URL, noteId);
        if (!StringUtils.isEmpty(msgUrl)) {
            try {
                return new URL(msgUrl).toExternalForm();
            } catch (MalformedURLException e) {
                MyLog.d(this, "Malformed URL from '" + msgUrl + "'", e);
            }
        }
        return alternativeNotePermalink(noteId);
    }

    protected String alternativeNotePermalink(long noteId) {
        return "";
    }

    public boolean shouldHaveUrl() {
        return originType.originHasUrl;
    }

    public boolean isNameValid() {
        return isNameValid(name);
    }

    public boolean isNameValid(String originNameToCheck) {
        return StringUtils.nonEmpty(originNameToCheck) && VALID_NAME_PATTERN.matcher(originNameToCheck).matches();
    }

    public URL getUrl() {
        return url;
    }

    public Uri fixUriForPermalink(Uri uri1) {
        return uri1;
    }

    public boolean hasHost() {
        return UrlUtils.hasHost(url);
    }

    public boolean urlIsValid() {
        if (originType.originHasUrl) {
            return UrlUtils.hasHost(url);
        } else {
            return true;
        }
    }

    /** OriginName to be used in {@link AccountName#getName()} */
    @NonNull
    public String getOriginInAccountName(String host) {
        List<Origin> origins = myContext.origins().allFromOriginInAccountNameAndHost(originType.getTitle(), host);
        switch (origins.size()) {
            case 0:
                return "";
            case 1:
                return originType.getTitle();  // No ambiguity, so we can use OriginType here
            default:
                return getName();
        }
    }

    /** Host to be used in {@link AccountName#getUniqueName()}  */
    @NonNull
    public String getAccountNameHost() {
        return getHost();
    }

    @NonNull
    public String getHost() {
        return UrlUtils.hasHost(url) ? url.getHost() : "";
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
        if (isEmpty()) return false;

        long count = 0;
        Cursor cursor = null;
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> "Origin hasChildren");
            return false;
        }
        try {
            String sql = "SELECT Count(*) FROM " + NoteTable.TABLE_NAME + " WHERE "
                    + NoteTable.ORIGIN_ID + "=" + id;
            cursor = db.rawQuery(sql, null);
            if (cursor.moveToNext()) {
                count = cursor.getLong(0);
            }
            cursor.close();
            if (count == 0) {
                sql = "SELECT Count(*) FROM " + ActorTable.TABLE_NAME + " WHERE "
                        + ActorTable.ORIGIN_ID + "=" + id;
                cursor = db.rawQuery(sql, null);
                if (cursor.moveToNext()) {
                    count = cursor.getLong(0);
                }
                cursor.close();
            }
            long countVal = count;
            MyLog.v(this, () -> this.toString() + " has " + countVal + " children");
        } catch (Exception e) {
            MyLog.e(this, "Error counting children", e);
        } finally {
            DbUtils.closeSilently(cursor);
        }
        return count != 0;
    }

    private static Origin fromType(MyContext myContext, OriginType originType) {
        Origin origin = originType.originFactory.apply(myContext);
        origin.url = origin.originType.getUrlDefault();
        origin.ssl = origin.originType.sslDefault;
        origin.allowHtml = origin.originType.allowHtmlDefault;
        origin.textLimit = origin.originType.textLimitDefault;
        origin.setInCombinedGlobalSearch(true);
        origin.setInCombinedPublicReload(true);
        return origin;
    }

    @Override
    public String toString() {
        if (this ==  EMPTY) {
            return "Origin:EMPTY";
        }
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

    @Override
    public int compareTo(@NonNull Origin o) {
        return getName().compareToIgnoreCase(o.getName());
    }


    public void assertContext() {
        if (!myContext.isReady()) {
            fail("Origin context should be ready " + this +
                    "\ncontext: " + myContext);
        }
        if (myContext.getDatabase() == null) {
            fail("Origin context should have database " + this +
                    "\ncontext: " + myContext);
        }
    }


    /**
     * The reference may be in the form of @username, @webfingerId, or wibfingerId, without "@" before it
     * @return index of the first position, where the username/webfingerId may start, -1 if not found
     */
    public ActorReference getActorReference(String text, int textStart) {
        if (StringUtils.isEmpty(text) || textStart >= text.length()) return ActorReference.EMPTY;

        int indexOfReference = text.indexOf(actorReferenceChar(), textStart);
        GroupType groupType = GroupType.UNKNOWN;
        if (groupActorReferenceChar().isPresent()) {
            int indexOfGroupReference = text.indexOf(groupActorReferenceChar().get(), textStart);
            if (indexOfGroupReference >= textStart &&
                    (indexOfReference < textStart || indexOfGroupReference < indexOfReference)) {
                indexOfReference = indexOfGroupReference;
                groupType = GroupType.GENERIC;
            }
        }
        if (indexOfReference < textStart) return ActorReference.EMPTY;

        if (indexOfReference == textStart) return new ActorReference(textStart + 1, groupType);

        if (USERNAME_CHARS.indexOf(text.charAt(indexOfReference - 1)) < 0) {
            return new ActorReference(indexOfReference + 1, groupType);
        } else if (groupType == GroupType.GENERIC) {
            // Group reference shouldn't have username chars before it
            return getActorReference(text, indexOfReference + 1);
        }

        // username part of WebfingerId before @ ?
        int ind = indexOfReference - 1;
        while (ind > textStart) {
            if (USERNAME_CHARS.indexOf(text.charAt(ind - 1)) < 0) break;
            ind--;
        }
        return new ActorReference(ind, groupType);
    }

    public boolean isReferenceChar(CharSequence text, int cursor) {
        if (text == null || cursor < 0 || cursor >= text.length()) {
            return false;
        }
        return isReferenceChar(text.charAt(cursor));
    }

    public boolean isReferenceChar(char c) {
        if (c == actorReferenceChar()) return true;
        return groupActorReferenceChar().map(rc -> rc == c).orElse(false);
    }

    public char actorReferenceChar() {
        return '@';
    }

    public Optional<Character> groupActorReferenceChar() {
        return Optional.empty();
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

        public Builder(MyContext myContext, OriginType originType) {
            origin = fromType(myContext, originType);
        }

        /**
         * Loading persistent Origin
         */
        public Builder(MyContext myContext, Cursor cursor) {
            OriginType originType1 = OriginType.fromId(
                    DbUtils.getLong(cursor, OriginTable.ORIGIN_TYPE_ID));
            origin = fromType(myContext, originType1);
            origin.id = DbUtils.getLong(cursor, OriginTable._ID);
            origin.name = DbUtils.getString(cursor, OriginTable.ORIGIN_NAME);
            setHostOrUrl(DbUtils.getString(cursor, OriginTable.ORIGIN_URL));
            setSsl(DbUtils.getBoolean(cursor, OriginTable.SSL));
            setSslMode(SslModeEnum.fromId(DbUtils.getLong(cursor, OriginTable.SSL_MODE)));
            
            origin.allowHtml = DbUtils.getBoolean(cursor, OriginTable.ALLOW_HTML);

            int textLimit = DbUtils.getInt(cursor, OriginTable.TEXT_LIMIT);
            setTextLimit(textLimit > 0
                    ? textLimit
                    : (originType1.textLimitDefault > 0
                        ? originType1.textLimitDefault
                        : OriginType.TEXT_LIMIT_MAXIMUM));
            origin.setInCombinedGlobalSearch(DbUtils.getBoolean(cursor,
                    OriginTable.IN_COMBINED_GLOBAL_SEARCH));
            origin.setInCombinedPublicReload(DbUtils.getBoolean(cursor,
                    OriginTable.IN_COMBINED_PUBLIC_RELOAD));
            setMentionAsWebFingerId(DbUtils.getTriState(cursor, OriginTable.MENTION_AS_WEBFINGER_ID));
            setUseLegacyHttpProtocol(DbUtils.getTriState(cursor, OriginTable.USE_LEGACY_HTTP));
        }

        protected void setTextLimit(int textLimit) {
            if (textLimit <= 0) {
                origin.textLimit = OriginType.TEXT_LIMIT_MAXIMUM;
            } else {
                origin.textLimit = textLimit;
            }
        }
        
        public Builder(Origin original) {
            origin = fromType(original.myContext, original.originType);
            origin.id = original.id;
            origin.name = original.name;
            setUrl(original.url);
            setSsl(original.ssl);
            setSslMode(original.sslMode);
            setHtmlContentAllowed(original.allowHtml);
            setTextLimit(original.getTextLimit());
            setInCombinedGlobalSearch(original.inCombinedGlobalSearch);
            setInCombinedPublicReload(original.inCombinedPublicReload);
            setMentionAsWebFingerId(original.mMentionAsWebFingerId);
            origin.mUseLegacyHttpProtocol = original.mUseLegacyHttpProtocol;
            origin.isValid = origin.calcIsValid();
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
            if (StringUtils.isEmpty(nameIn)) {
                return "";
            }
            // Test with: http://www.regexplanet.com/advanced/java/index.html
            return DOTS_PATTERN.matcher(
                INVALID_NAME_PART_PATTERN.matcher(
                        org.apache.commons.lang3.StringUtils.stripAccents(nameIn.trim())
                ).replaceAll(".")
            ).replaceAll(".");
        }

        public Builder setUrl(URL urlIn) {
           return urlIn == null ? this : setHostOrUrl(urlIn.toExternalForm());
        }
        
        public Builder setHostOrUrl(String hostOrUrl) {
            if (origin.originType.originHasUrl) {
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
        
        public Builder save(OriginConfig config) {
            setTextLimit(config.textLimit);
            save();
            return this;
        }

        public Builder save() {
            saved = false;
            origin.isValid = origin.calcIsValid(); // TODO: refactor...
            if (!origin.isValid()) {
                MyLog.v(this, () -> "Is not valid: " + origin.toString());
                return this;
            }
            if (origin.id == 0) {
                Origin existing = origin.myContext.origins()
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
            values.put(OriginTable.SSL_MODE, origin.getSslMode().id);
            values.put(OriginTable.ALLOW_HTML, origin.allowHtml);
            values.put(OriginTable.TEXT_LIMIT, origin.getTextLimit());
            values.put(OriginTable.IN_COMBINED_GLOBAL_SEARCH, origin.inCombinedGlobalSearch);
            values.put(OriginTable.IN_COMBINED_PUBLIC_RELOAD, origin.inCombinedPublicReload);
            values.put(OriginTable.MENTION_AS_WEBFINGER_ID, origin.mMentionAsWebFingerId.id);
            values.put(OriginTable.USE_LEGACY_HTTP, origin.useLegacyHttpProtocol().id);

            boolean changed = false;
            if (origin.id == 0) {
                values.put(OriginTable.ORIGIN_NAME, origin.name);
                values.put(OriginTable.ORIGIN_TYPE_ID, origin.originType.getId());
                origin.id = DbUtils.addRowWithRetry(getMyContext(), OriginTable.TABLE_NAME, values, 3);
                changed = origin.isPersistent();
            } else {
                changed = (DbUtils.updateRowWithRetry(getMyContext(), OriginTable.TABLE_NAME, origin.id,
                        values, 3) != 0);
            }
            if (changed && getMyContext().isReady()) {
                MyPreferences.onPreferencesChanged();
            }
            saved = changed;
            return this;
        }

        public boolean delete() {
            boolean deleted = false;
            if (!origin.hasChildren()) {
                SQLiteDatabase db = getMyContext().getDatabase();
                if (db == null) {
                    MyLog.databaseIsNull(() -> "delete");
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

        public MyContext getMyContext() {
            return origin.myContext;
        }

        @Override
        public String toString() {
            return "Builder" + (isSaved() ? "saved " : " not") + " saved; " + origin.toString();
        }
    }
}
