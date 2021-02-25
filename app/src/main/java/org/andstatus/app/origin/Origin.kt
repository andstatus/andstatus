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
package org.andstatus.app.origin

import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.annotation.StringRes
import org.andstatus.app.account.AccountName
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyProvider
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.database.table.OriginTable
import org.andstatus.app.net.http.SslModeEnum
import org.andstatus.app.net.social.Patterns
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SelectionAndArgs
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import org.apache.commons.lang3.StringUtils
import org.junit.Assert
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.regex.Pattern

/**
 * Social network (twitter.com, identi.ca, ... ) where notes are being
 * created (it's the "Origin" of the notes)
 *
 * @author yvolk@yurivolkov.com
 */
open class Origin internal constructor(val myContext: MyContext, private val originType: OriginType) : Comparable<Origin>, IsEmpty {
    val shortUrlLength: Int
    /** the Origin name, unique in the application */
    var name = ""
    /** the OriginId in MyDatabase. 0 means that this system doesn't exist */
    var id: Long = 0
    var url: URL? = null
    protected var ssl = true
    private var sslMode: SslModeEnum = SslModeEnum.SECURE
    private var mUseLegacyHttpProtocol: TriState = TriState.UNKNOWN
    private var allowHtml = false

    /**
     * Maximum number of characters in a note
     */
    private var textLimit: Int = TEXT_LIMIT_MAXIMUM

    /** Include this system in Global Search while in Combined Timeline  */
    private var inCombinedGlobalSearch = false
    private var inCombinedPublicReload = false
    private var mMentionAsWebFingerId: TriState = TriState.UNKNOWN
    private var isValid = false
    fun getOriginType(): OriginType {
        return originType
    }

    /**
     * Was this Origin stored for future reuse?
     */
    fun isPersistent(): Boolean {
        return id != 0L
    }

    override fun isEmpty(): Boolean {
        return this === EMPTY || originType === OriginType.UNKNOWN // TODO avoid second case
    }

    fun isValid(): Boolean {
        return isValid
    }

    private fun calcIsValid(): Boolean {
        return (nonEmpty()
                && isNameValid()
                && urlIsValid()
                && (isSsl() == originType.sslDefault || originType.canChangeSsl))
    }

    fun isOAuthDefault(): Boolean {
        return originType.isOAuthDefault
    }

    /**
     * @return the Can OAuth connection setting can be turned on/off from the
     * default setting
     */
    fun canChangeOAuth(): Boolean {
        return originType.canChangeOAuth
    }

    fun isUsernameValid(username: String?): Boolean {
        return !username.isNullOrEmpty() && originType.usernameRegExPattern.matcher(username).matches()
    }

    fun usernameToId(username: String?): Long {
        if (username.isNullOrEmpty()) return 0
        val key = "$id;$username"
        val cachedId = myContext.users().originIdAndUsernameToActorId[key]
        if (cachedId != null) return cachedId
        val storedId = MyQuery.usernameToId(myContext, id, username, true)
        if (storedId != 0L) {
            myContext.users().originIdAndUsernameToActorId[key] = storedId
        }
        return storedId
    }

    /**
     * Calculates number of Characters left for this note taking shortened
     * URL's length into account.
     *
     * @author yvolk@yurivolkov.com
     */
    fun charactersLeftForNote(html: String?): Int {
        var textLength = 0
        if (!html.isNullOrEmpty()) {
            val textToPost = MyHtml.fromContentStored(html, originType.textMediaTypeToPost)
            textLength = textToPost.length
            if (shortUrlLength > 0) {
                // Now try to adjust the length taking links into account
                val ss = SpannableString.valueOf(textToPost)
                Linkify.addLinks(ss, Linkify.WEB_URLS)
                val spans = ss.getSpans(0, textLength, URLSpan::class.java)
                for (ind1 in spans.indices) {
                    val start = ss.getSpanStart(spans[ind1])
                    val end = ss.getSpanEnd(spans[ind1])
                    textLength += shortUrlLength - (end - start)
                }
            }
        }
        return textLimit - textLength
    }

    open fun alternativeTermForResourceId(@StringRes resId: Int): Int {
        return resId
    }

    open fun getNotePermalink(noteId: Long): String? {
        val msgUrl = MyQuery.noteIdToStringColumnValue(NoteTable.URL, noteId)
        if (msgUrl.isNotEmpty()) {
            try {
                return URL(msgUrl).toExternalForm()
            } catch (e: MalformedURLException) {
                MyLog.d(this, "Malformed URL from '$msgUrl'", e)
            }
        }
        return alternativeNotePermalink(noteId)
    }

    protected open fun alternativeNotePermalink(noteId: Long): String? {
        return ""
    }

    fun shouldHaveUrl(): Boolean {
        return originType.originHasUrl
    }

    fun isNameValid(): Boolean {
        return isNameValid(name)
    }

    fun isNameValid(originNameToCheck: String?): Boolean {
        return !originNameToCheck.isNullOrEmpty() && VALID_NAME_PATTERN.matcher(originNameToCheck).matches()
    }

    open fun fixUriForPermalink(uri1: Uri): Uri {
        return uri1
    }

    fun hasHost(): Boolean {
        return UrlUtils.hasHost(url)
    }

    fun urlIsValid(): Boolean {
        return if (originType.originHasUrl) {
            UrlUtils.hasHost(url)
        } else {
            true
        }
    }

    /** OriginName to be used in [AccountName.getName]  */
    fun getOriginInAccountName(host: String?): String {
        val origins = myContext.origins().allFromOriginInAccountNameAndHost(originType.title, host)
        return when (origins.size) {
            0 -> ""
            1 -> originType.title // No ambiguity, so we can use OriginType here
            else -> name
        }
    }

    /** Host to be used in [AccountName.getUniqueName]   */
    open fun getAccountNameHost(): String {
        return getHost()
    }

    fun getHost(): String {
        return if (UrlUtils.hasHost(url)) url?.getHost() ?: "" else ""
    }

    fun isSsl(): Boolean {
        return ssl
    }

    fun getSslMode(): SslModeEnum {
        return sslMode
    }

    fun useLegacyHttpProtocol(): TriState {
        return mUseLegacyHttpProtocol
    }

    fun isHtmlContentAllowed(): Boolean {
        return allowHtml
    }

    fun isSyncedForAllOrigins(isSearch: Boolean): Boolean {
        if (isSearch) {
            if (isInCombinedGlobalSearch()) {
                return true
            }
        } else {
            if (isInCombinedPublicReload()) {
                return true
            }
        }
        return false
    }

    fun setInCombinedGlobalSearch(inCombinedGlobalSearch: Boolean) {
        if (originType.isSearchTimelineSyncable()) {
            this.inCombinedGlobalSearch = inCombinedGlobalSearch
        }
    }

    fun isInCombinedGlobalSearch(): Boolean {
        return inCombinedGlobalSearch
    }

    fun isInCombinedPublicReload(): Boolean {
        return inCombinedPublicReload
    }

    private fun isMentionAsWebFingerIdDefault(): Boolean {
        when (originType) {
            OriginType.PUMPIO -> return true
            OriginType.TWITTER -> return false
            else -> {
            }
        }
        return getTextLimit() == 0 || getTextLimit() >= TEXT_LIMIT_FOR_WEBFINGER_ID
    }

    fun getMentionAsWebFingerId(): TriState {
        return mMentionAsWebFingerId
    }

    fun isMentionAsWebFingerId(): Boolean {
        return mMentionAsWebFingerId.toBoolean(isMentionAsWebFingerIdDefault())
    }

    fun hasAccounts(): Boolean {
        return myContext.accounts().getFirstPreferablySucceededForOrigin(this).isValid()
    }

    fun hasNotes(): Boolean {
        if (isEmpty()) return false
        val db = myContext.getDatabase()
        if (db == null) {
            MyLog.databaseIsNull { "Origin hasChildren" }
            return true
        }
        val sql = "SELECT * FROM " + NoteTable.TABLE_NAME + " WHERE " + NoteTable.ORIGIN_ID + "=" + id
        return MyQuery.dExists(db, sql)
    }

    override fun toString(): String {
        return if (this === EMPTY) {
            "Origin:EMPTY"
        } else "Origin:{" + (if (isValid()) "" else "(invalid) ") + "name:" + name +
        ", type:" + originType +
        (if (url != null) ", url:" + url else "") +
        (if (isSsl()) ", " + getSslMode() else "") +
        (if (getMentionAsWebFingerId() != TriState.UNKNOWN) ", mentionAsWf:" + getMentionAsWebFingerId() else "") +
        "}"
    }

    fun getTextLimit(): Int {
        return textLimit
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is Origin) return false
        return if (id != other.id) false else StringUtil.equalsNotEmpty(name, other.name)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (id xor (id ushr 32)).toInt()
        return result
    }

    fun setInCombinedPublicReload(inCombinedPublicReload: Boolean) {
        if (originType.isPublicTimeLineSyncable()) {
            this.inCombinedPublicReload = inCombinedPublicReload
        }
    }

    override operator fun compareTo(other: Origin): Int {
        return name.compareTo(other.name, ignoreCase = true)
    }

    fun assertContext() {
        if (!myContext.isReady()) {
            Assert.fail("""
    Origin context should be ready $this
    context: $myContext
    """.trimIndent())
        }
        if (myContext.getDatabase() == null) {
            Assert.fail("""
    Origin context should have database $this
    context: $myContext
    """.trimIndent())
        }
    }

    /**
     * The reference may be in the form of @username, @webfingerId, or wibfingerId, without "@" before it
     * @return index of the first position, where the username/webfingerId may start, -1 if not found
     */
    fun getActorReference(text: String?, textStart: Int): ActorReference {
        if (text.isNullOrEmpty() || textStart >= text.length) return ActorReference.EMPTY
        var indexOfReference = text.indexOf(actorReferenceChar(), textStart)
        var groupType = GroupType.UNKNOWN
        if (groupActorReferenceChar().isPresent()) {
            val indexOfGroupReference = text.indexOf(groupActorReferenceChar().get(), textStart)
            if (indexOfGroupReference >= textStart &&
                    (indexOfReference < textStart || indexOfGroupReference < indexOfReference)) {
                indexOfReference = indexOfGroupReference
                groupType = GroupType.GENERIC
            }
        }
        if (indexOfReference < textStart) return ActorReference.EMPTY
        if (indexOfReference == textStart) return ActorReference(textStart + 1, groupType)
        if (Patterns.USERNAME_CHARS.indexOf(text.get(indexOfReference - 1)) < 0) {
            return ActorReference(indexOfReference + 1, groupType)
        } else if (groupType == GroupType.GENERIC) {
            // Group reference shouldn't have username chars before it
            return getActorReference(text, indexOfReference + 1)
        }

        // username part of WebfingerId before @ ?
        var ind = indexOfReference - 1
        while (ind > textStart) {
            if (Patterns.USERNAME_CHARS.indexOf(text.get(ind - 1)) < 0) break
            ind--
        }
        return ActorReference(ind, groupType)
    }

    fun isReferenceChar(text: CharSequence?, cursor: Int): Boolean {
        return if (text == null || cursor < 0 || cursor >= text.length) {
            false
        } else isReferenceChar(text[cursor])
    }

    fun isReferenceChar(c: Char): Boolean {
        return if (c == actorReferenceChar()) true
        else groupActorReferenceChar()
                .map { rc: Char? -> rc == c }
                .orElse(false)
    }

    fun actorReferenceChar(): Char {
        return '@'
    }

    open fun groupActorReferenceChar(): Optional<Char> {
        return Optional.empty()
    }

    class Builder {
        private val origin: Origin

        /*
         * Result of the last "save" action
         */
        private var saved = false
        fun isSaved(): Boolean {
            return saved
        }

        constructor(myContext: MyContext, originType: OriginType) {
            origin = fromType(myContext, originType)
        }

        /**
         * Loading persistent Origin
         */
        constructor(myContext: MyContext, cursor: Cursor) {
            val originType1: OriginType = OriginType.fromId(
                    DbUtils.getLong(cursor, OriginTable.ORIGIN_TYPE_ID))
            origin = fromType(myContext, originType1)
            origin.id = DbUtils.getLong(cursor, BaseColumns._ID)
            origin.name = DbUtils.getString(cursor, OriginTable.ORIGIN_NAME)
            setHostOrUrl(DbUtils.getString(cursor, OriginTable.ORIGIN_URL))
            setSsl(DbUtils.getBoolean(cursor, OriginTable.SSL))
            setSslMode(SslModeEnum.fromId(DbUtils.getLong(cursor, OriginTable.SSL_MODE)))
            origin.allowHtml = DbUtils.getBoolean(cursor, OriginTable.ALLOW_HTML)
            val textLimit = DbUtils.getInt(cursor, OriginTable.TEXT_LIMIT)
            setTextLimit(if (textLimit > 0) textLimit else
                if (originType1.textLimitDefault > 0) originType1.textLimitDefault else TEXT_LIMIT_MAXIMUM)
            origin.setInCombinedGlobalSearch(DbUtils.getBoolean(cursor,
                    OriginTable.IN_COMBINED_GLOBAL_SEARCH))
            origin.setInCombinedPublicReload(DbUtils.getBoolean(cursor,
                    OriginTable.IN_COMBINED_PUBLIC_RELOAD))
            setMentionAsWebFingerId(DbUtils.getTriState(cursor, OriginTable.MENTION_AS_WEBFINGER_ID))
            setUseLegacyHttpProtocol(DbUtils.getTriState(cursor, OriginTable.USE_LEGACY_HTTP))
        }

        protected fun setTextLimit(textLimit: Int) {
            if (textLimit <= 0) {
                origin.textLimit = TEXT_LIMIT_MAXIMUM
            } else {
                origin.textLimit = textLimit
            }
        }

        constructor(original: Origin) {
            origin = fromType(original.myContext, original.originType)
            origin.id = original.id
            origin.name = original.name
            setUrl(original.url)
            setSsl(original.ssl)
            setSslMode(original.sslMode)
            setHtmlContentAllowed(original.allowHtml)
            setTextLimit(original.getTextLimit())
            setInCombinedGlobalSearch(original.inCombinedGlobalSearch)
            setInCombinedPublicReload(original.inCombinedPublicReload)
            setMentionAsWebFingerId(original.mMentionAsWebFingerId)
            origin.mUseLegacyHttpProtocol = original.mUseLegacyHttpProtocol
            origin.isValid = origin.calcIsValid()
        }

        fun build(): Origin {
            return Builder(origin).origin
        }

        fun setName(nameIn: String?): Builder {
            val name = correctedName(nameIn)
            if (!origin.isPersistent() && origin.isNameValid(name)) {
                origin.name = name
            }
            return this
        }

        private fun correctedName(nameIn: String?): String {
            if (nameIn.isNullOrEmpty()) return ""

            if (origin.isNameValid(nameIn)) {
                return nameIn
            }
            return DOTS_PATTERN.matcher(
                    INVALID_NAME_PART_PATTERN.matcher(
                            StringUtils.stripAccents(nameIn.trim { it <= ' ' })
                    ).replaceAll(".")
            ).replaceAll(".")
            // Test with: http://www.regexplanet.com/advanced/java/index.html
        }

        fun setUrl(urlIn: URL?): Builder {
            return if (urlIn == null) this else setHostOrUrl(urlIn.toExternalForm())
        }

        fun setHostOrUrl(hostOrUrl: String?): Builder {
            if (origin.originType.originHasUrl) {
                var url1 = UrlUtils.buildUrl(hostOrUrl, origin.isSsl())
                if (url1 != null) {
                    if (!UrlUtils.isHostOnly(url1) && !url1.toExternalForm().endsWith("/")) {
                        url1 = UrlUtils.fromString(url1.toExternalForm() + "/")
                    }
                    origin.url = url1
                }
            }
            return this
        }

        fun setSsl(ssl: Boolean): Builder {
            if (origin.originType.canChangeSsl) {
                origin.ssl = ssl
                setUrl(origin.url)
            }
            return this
        }

        fun setSslMode(mode: SslModeEnum): Builder {
            origin.sslMode = mode
            return this
        }

        fun setHtmlContentAllowed(allowHtml: Boolean): Builder {
            origin.allowHtml = allowHtml
            return this
        }

        fun setInCombinedGlobalSearch(inCombinedGlobalSearch: Boolean): Builder {
            origin.setInCombinedGlobalSearch(inCombinedGlobalSearch)
            return this
        }

        fun setInCombinedPublicReload(inCombinedPublicReload: Boolean): Builder {
            origin.setInCombinedPublicReload(inCombinedPublicReload)
            return this
        }

        fun setMentionAsWebFingerId(mentionAsWebFingerId: TriState): Builder {
            origin.mMentionAsWebFingerId = mentionAsWebFingerId
            return this
        }

        fun setUseLegacyHttpProtocol(useLegacyHttpProtocol: TriState): Builder {
            origin.mUseLegacyHttpProtocol = useLegacyHttpProtocol
            return this
        }

        fun save(config: OriginConfig): Builder {
            setTextLimit(config.textLimit)
            save()
            return this
        }

        fun save(): Builder {
            saved = false
            origin.isValid = origin.calcIsValid() // TODO: refactor...
            if (!origin.isValid()) {
                MyLog.v(this) { "Is not valid: " + origin.toString() }
                return this
            }
            if (origin.id == 0L) {
                val existing = origin.myContext.origins()
                        .fromName(origin.name)
                if (existing.isPersistent()) {
                    if (origin.originType !== existing.originType) {
                        MyLog.w(this, "Origin with this name and other type already exists $existing")
                        return this
                    }
                    origin.id = existing.id
                }
            }
            val values = ContentValues()
            values.put(OriginTable.ORIGIN_URL, origin.url?.toExternalForm() ?: "")
            values.put(OriginTable.SSL, origin.ssl)
            values.put(OriginTable.SSL_MODE, origin.getSslMode().id)
            values.put(OriginTable.ALLOW_HTML, origin.allowHtml)
            values.put(OriginTable.TEXT_LIMIT, origin.getTextLimit())
            values.put(OriginTable.IN_COMBINED_GLOBAL_SEARCH, origin.inCombinedGlobalSearch)
            values.put(OriginTable.IN_COMBINED_PUBLIC_RELOAD, origin.inCombinedPublicReload)
            values.put(OriginTable.MENTION_AS_WEBFINGER_ID, origin.mMentionAsWebFingerId.id)
            values.put(OriginTable.USE_LEGACY_HTTP, origin.useLegacyHttpProtocol().id)
            val changed: Boolean
            if (origin.id == 0L) {
                values.put(OriginTable.ORIGIN_NAME, origin.name)
                values.put(OriginTable.ORIGIN_TYPE_ID, origin.originType.getId())
                DbUtils.addRowWithRetry(getMyContext(), OriginTable.TABLE_NAME, values, 3)
                        .onSuccess { idAdded: Long -> origin.id = idAdded }
                changed = origin.isPersistent()
            } else {
                changed = DbUtils.updateRowWithRetry(getMyContext(), OriginTable.TABLE_NAME, origin.id,
                        values, 3)
                        .onFailure { e: Throwable? -> MyLog.w("Origin", "Failed to save $this") }
                        .isSuccess
            }
            if (changed && getMyContext().isReady()) {
                MyPreferences.onPreferencesChanged()
            }
            saved = changed
            return this
        }

        fun delete(): Boolean {
            val sa = SelectionAndArgs()
            sa.addSelection(ActivityTable.TABLE_NAME + "." + ActivityTable.ORIGIN_ID + "=" + origin.id)
            val deletedActivities: Long = MyProvider.deleteActivities(getMyContext(),
                    sa.selection, sa.selectionArgs, false).toLong()
            val deletedActors = MyQuery.getLongs(getMyContext(), "SELECT " + BaseColumns._ID
                    + " FROM " + ActorTable.TABLE_NAME
                    + " WHERE " + ActorTable.ORIGIN_ID + "=" + origin.id).stream()
                    .mapToLong { actorId: Long -> MyProvider.deleteActor(getMyContext(), actorId) }
                    .sum()
            val deleted = (!origin.hasNotes()
                    && MyProvider.delete(getMyContext(), OriginTable.TABLE_NAME, BaseColumns._ID, origin.id) > 0)
            if (deleted) {
                MyLog.i(this, "Deleted Origin " + origin
                        + ", its activities: " + deletedActivities
                        + ", actors: " + deletedActors)
                getMyContext().setExpired { "Origin $origin deleted" }
            }
            return deleted
        }

        fun getMyContext(): MyContext {
            return origin.myContext
        }

        override fun toString(): String {
            return "Builder" + (if (isSaved()) "saved " else " not") + " saved; " + origin.toString()
        }
    }

    companion object {
        const val TEXT_LIMIT_FOR_WEBFINGER_ID = 200
        val EMPTY: Origin = fromType(MyContext.EMPTY, OriginType.UNKNOWN)
        private val VALID_NAME_CHARS: String = "a-zA-Z_0-9/.-"
        private val VALID_NAME_PATTERN = Pattern.compile("[" + VALID_NAME_CHARS + "]+")
        private val INVALID_NAME_PART_PATTERN = Pattern.compile("[^" + VALID_NAME_CHARS + "]+")
        private val DOTS_PATTERN = Pattern.compile("[.]+")
        val KEY_ORIGIN_NAME: String = "origin_name"

        private fun fromType(myContext: MyContext, originType: OriginType): Origin {
            val origin = originType.originFactory.apply(myContext)
            origin.url = origin.originType.urlDefault
            origin.ssl = origin.originType.sslDefault
            origin.allowHtml = origin.originType.allowHtmlDefault
            origin.textLimit = origin.originType.textLimitDefault
            origin.setInCombinedGlobalSearch(true)
            origin.setInCombinedPublicReload(true)
            return origin
        }
    }

    init {
        shortUrlLength = originType.shortUrlLengthDefault.value
    }
}