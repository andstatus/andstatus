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
package org.andstatus.app.origin

import android.content.Context
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.lang.SelectableEnum
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.net.http.HttpConnectionBasic
import org.andstatus.app.net.http.HttpConnectionOAuthApache
import org.andstatus.app.net.http.HttpConnectionOAuthJavaNet
import org.andstatus.app.net.http.HttpConnectionOAuthMastodon
import org.andstatus.app.net.social.Connection
import org.andstatus.app.net.social.ConnectionEmpty
import org.andstatus.app.net.social.ConnectionMastodon
import org.andstatus.app.net.social.ConnectionTheTwitter
import org.andstatus.app.net.social.ConnectionTwitterGnuSocial
import org.andstatus.app.net.social.Patterns
import org.andstatus.app.net.social.activitypub.ConnectionActivityPub
import org.andstatus.app.net.social.pumpio.ConnectionPumpio
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import java.net.URL
import java.util.*
import java.util.function.Function
import java.util.regex.Pattern

const val SIMPLE_USERNAME_EXAMPLES: String = "AndStatus user357 peter"
private const val BASIC_PATH_DEFAULT: String = "api"
private const val OAUTH_PATH_DEFAULT: String = "oauth"
const val TEXT_LIMIT_MAXIMUM = 100000

enum class OriginType(
    private val id: Long, val title: String, api: ApiEnum,
    hasNoteName: HasNoteName, hasNoteSummary: HasNoteSummary, hasListsOfUser: HasListsOfUser,
    publicChangeAllowed: PublicChangeAllowed, followersChangeAllowed: FollowersChangeAllowed,
    sensitiveChangeAllowed: SensitiveChangeAllowed,
    shortUrlLength: ShortUrlLength
) : SelectableEnum {
    /** [Mastodon at GitHub](https://github.com/Gargron/mastodon)  */
    MASTODON(
        4, "Mastodon", ApiEnum.MASTODON,
        HasNoteName.NO, HasNoteSummary.YES, HasListsOfUser.YES,
        PublicChangeAllowed.YES, FollowersChangeAllowed.YES, SensitiveChangeAllowed.YES,
        ShortUrlLength.of(0)
    ),

    /**
     * Origin type for Twitter system
     * [Twitter Developers' documentation](https://dev.twitter.com/docs)
     */
    TWITTER(
        1, "Twitter", ApiEnum.TWITTER1P1,
        HasNoteName.NO, HasNoteSummary.NO, HasListsOfUser.YES,
        PublicChangeAllowed.NO, FollowersChangeAllowed.NO, SensitiveChangeAllowed.NO,
        ShortUrlLength.of(23)
    ),
    ACTIVITYPUB(
        5, "ActivityPub", ApiEnum.ACTIVITYPUB,
        HasNoteName.YES, HasNoteSummary.YES, HasListsOfUser.NO,
        PublicChangeAllowed.YES, FollowersChangeAllowed.YES, SensitiveChangeAllowed.YES,
        ShortUrlLength.of(0)
    ) {
        override fun getContentType(): Optional<String> {
            return Optional.of("application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"")
        }
    },
    GNUSOCIAL(
        3, "GnuSocial", ApiEnum.GNUSOCIAL_TWITTER,
        HasNoteName.NO, HasNoteSummary.NO, HasListsOfUser.NO,
        PublicChangeAllowed.NO, FollowersChangeAllowed.NO, SensitiveChangeAllowed.NO,
        ShortUrlLength.of(0)
    ),

    /**
     * Origin type for the pump.io system
     * Till July of 2013 (and v.1.16 of AndStatus) the API was:
     * [Twitter-compatible identi.ca API](http://status.net/wiki/Twitter-compatible_API)
     * Since July 2013 the API is [pump.io API](https://github.com/e14n/pump.io/blob/master/API.md)
     */
    PUMPIO(
        2, "Pump.io", ApiEnum.PUMPIO,
        HasNoteName.YES, HasNoteSummary.NO, HasListsOfUser.NO,
        PublicChangeAllowed.YES, FollowersChangeAllowed.YES, SensitiveChangeAllowed.NO,
        ShortUrlLength.of(0)
    ),
    UNKNOWN(
        0, "?", ApiEnum.UNKNOWN_API,
        HasNoteName.NO, HasNoteSummary.NO, HasListsOfUser.NO,
        PublicChangeAllowed.NO, FollowersChangeAllowed.NO, SensitiveChangeAllowed.NO,
        ShortUrlLength.of(0)
    ) {
        override fun isSelectable(): Boolean {
            return false
        }
    };

    private enum class HasNoteName {
        YES, NO
    }

    private enum class HasNoteSummary {
        YES, NO
    }

    private enum class HasListsOfUser {
        YES, NO
    }

    private enum class PublicChangeAllowed {
        YES, NO
    }

    private enum class FollowersChangeAllowed {
        YES, NO
    }

    private enum class SensitiveChangeAllowed {
        YES, NO
    }

    internal class ShortUrlLength private constructor(val value: Int) {
        companion object {
            fun of(length: Int): ShortUrlLength {
                return ShortUrlLength(length)
            }
        }
    }

    /**
     * Connection APIs known
     */
    private enum class ApiEnum {
        UNKNOWN_API,

        /** Twitter API v.1 https://dev.twitter.com/docs/api/1      */
        TWITTER1P0,

        /** Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1  */
        TWITTER1P1,

        /** GNU social (former: Status Net) Twitter compatible API http://status.net/wiki/Twitter-compatible_API   */
        GNUSOCIAL_TWITTER,

        /** https://github.com/e14n/pump.io/blob/master/API.md  */
        PUMPIO,

        /** https://github.com/Gargron/mastodon/wiki/API  */
        MASTODON,

        /** https://www.w3.org/TR/activitypub/  */
        ACTIVITYPUB
    }

    companion object {
        val ORIGIN_TYPE_DEFAULT: OriginType = TWITTER

        fun fromId(id: Long): OriginType {
            for (`val` in values()) {
                if (`val`.id == id) {
                    return `val`
                }
            }
            return UNKNOWN
        }

        fun fromCode(code: String?): OriginType {
            for (value in values()) {
                if (value.getCode().equals(code, ignoreCase = true)) {
                    return value
                }
            }
            return UNKNOWN
        }

        fun fromTitle(titleIn: String?): OriginType {
            return StringUtil.optNotEmpty(titleIn)
                    .flatMap { title: String? ->
                        Arrays.stream(values())
                                .filter { value: OriginType -> value.title.equals(title, ignoreCase = true) }
                                .findAny()
                    }
                    .orElse(UNKNOWN)
        }
    }

    val originHasUrl: Boolean
    var originFactory: Function<MyContext, Origin>
    private val connectionClass: Class<out Connection>
    private val httpConnectionClassOauth: Class<out HttpConnection?>
    private val httpConnectionClassBasic: Class<out HttpConnection?>
    private val allowEditing: Boolean

    /** Default OAuth setting  */
    var isOAuthDefault = true

    /** Can OAuth connection setting can be turned on/off from the default setting  */
    var canChangeOAuth = false
    protected var shouldSetNewUsernameManuallyIfOAuth = false

    /** May a User set username for the new Account/Actor manually?
     * This is only for no OAuth  */
    protected var shouldSetNewUsernameManuallyNoOAuth = false
    val usernameRegExPattern: Pattern
    val uniqueNameExamples: String

    /**
     * Length of the link after changing to the shortened link
     * 0 means that length doesn't change
     * For Twitter.com see [GET help/configuration](https://dev.twitter.com/docs/api/1.1/get/help/configuration)
     */
    internal val shortUrlLengthDefault: ShortUrlLength = shortUrlLength
    var sslDefault = true
    var canChangeSsl = false
    var allowHtmlDefault = true
    val textMediaTypePosted: TextMediaType
    val textMediaTypeToPost: TextMediaType

    /** Maximum number of characters in a note  */
    var textLimitDefault: Int = 0
    val urlDefault: URL?
    private val basicPath: String
    private val oauthPath: String
    private val isPublicTimeLineSyncable: Boolean
    private val isSearchTimelineSyncable: Boolean
    private val isPrivateTimelineSyncable: Boolean
    private val isInteractionsTimelineSyncable: Boolean
    val isPrivateNoteAllowsReply: Boolean
    val hasNoteName: Boolean = hasNoteName == HasNoteName.YES
    val hasNoteSummary: Boolean = hasNoteSummary == HasNoteSummary.YES
    val hasListsOfUser: Boolean = hasListsOfUser == HasListsOfUser.YES
    val visibilityChangeAllowed: Boolean = publicChangeAllowed == PublicChangeAllowed.YES
    val isFollowersChangeAllowed: Boolean = followersChangeAllowed == FollowersChangeAllowed.YES
    val isSensitiveChangeAllowed: Boolean = sensitiveChangeAllowed == SensitiveChangeAllowed.YES

    fun uniqueNameHasHost(): Boolean = this !== TWITTER

    fun getConnectionClass(): Class<out Connection?> = connectionClass

    fun getHttpConnectionClass(isOAuth: Boolean): Class<out HttpConnection?> {
        return if (fixIsOAuth(isOAuth)) {
            httpConnectionClassOauth
        } else {
            httpConnectionClassBasic
        }
    }

    override fun isSelectable(): Boolean {
        return true
    }

    override fun getCode(): String? = getId().toString()

    override fun title(context: Context?): CharSequence? = title

    override fun getDialogTitleResId(): Int = R.string.label_origin_type

    fun getId(): Long = id

    override fun toString(): String {
        return "OriginType: {id:$id, title:'$title'}"
    }

    fun fixIsOAuth(triStateOAuth: TriState): Boolean {
        return fixIsOAuth(triStateOAuth.toBoolean(isOAuthDefault))
    }

    fun fixIsOAuth(isOAuthIn: Boolean): Boolean {
        var fixed = isOAuthIn
        if (fixed != isOAuthDefault && !canChangeOAuth) {
            fixed = isOAuthDefault
        }
        return fixed
    }

    fun allowAttachmentForPrivateNote(): Boolean {
        return true // Currently for all...
    }

    fun isTimelineTypeSyncable(timelineType: TimelineType?): Boolean {
        return if (timelineType == null || !timelineType.isSyncable()) {
            false
        } else when (timelineType) {
            TimelineType.PUBLIC -> isPublicTimeLineSyncable
            TimelineType.SEARCH -> isSearchTimelineSyncable
            TimelineType.INTERACTIONS, TimelineType.UNREAD_NOTIFICATIONS, TimelineType.NOTIFICATIONS -> isInteractionsTimelineSyncable
            TimelineType.PRIVATE -> isPrivateTimelineSyncable
            else -> true
        }
    }

    fun isPublicTimeLineSyncable(): Boolean {
        return isPublicTimeLineSyncable
    }

    fun isSearchTimelineSyncable(): Boolean {
        return isSearchTimelineSyncable
    }

    fun partialPathToApiPath(partialPath: String): String {
        var apiPath = partialPath
        if (apiPath.isNotEmpty() && !apiPath.contains("://")) {
            apiPath = getBasicPath() + "/" + apiPath
        }
        return apiPath
    }

    fun getBasicPath(): String {
        return basicPath
    }

    fun getOauthPath(): String {
        return oauthPath
    }

    fun allowEditing(): Boolean {
        return allowEditing
    }

    fun isUsernameNeededToStartAddingNewAccount(isOAuth: Boolean): Boolean {
        return if (isOAuth) shouldSetNewUsernameManuallyIfOAuth else shouldSetNewUsernameManuallyNoOAuth
    }

    open fun getContentType(): Optional<String> {
        return Optional.empty()
    }

    fun getMaxAttachmentsToSend(): Int {
        return OriginConfig.getMaxAttachmentsToSend(this)
    }

    fun isPrivatePostsSupported(): Boolean {
        return this !== TWITTER
    }

    init {
        when (api) {
            ApiEnum.TWITTER1P1 -> {
                isOAuthDefault = true
                // Starting from 2010-09 twitter.com allows OAuth only
                canChangeOAuth = false
                originHasUrl = true
                shouldSetNewUsernameManuallyIfOAuth = false
                shouldSetNewUsernameManuallyNoOAuth = true
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN
                uniqueNameExamples = SIMPLE_USERNAME_EXAMPLES
                textLimitDefault = 280
                urlDefault = UrlUtils.fromString("https://api.twitter.com")
                basicPath = "1.1"
                oauthPath = OAUTH_PATH_DEFAULT
                originFactory = Function { myContext: MyContext -> OriginTwitter(myContext, this) }
                connectionClass = ConnectionTheTwitter::class.java
                httpConnectionClassOauth = HttpConnectionOAuthApache::class.java
                httpConnectionClassBasic = HttpConnectionBasic::class.java
                isPublicTimeLineSyncable = false
                isSearchTimelineSyncable = true
                isPrivateTimelineSyncable = false
                isInteractionsTimelineSyncable = true
                allowEditing = false
                isPrivateNoteAllowsReply = false
                textMediaTypePosted = TextMediaType.PLAIN_ESCAPED
                textMediaTypeToPost = TextMediaType.PLAIN
            }
            ApiEnum.PUMPIO -> {
                isOAuthDefault = true
                canChangeOAuth = false
                originHasUrl = false
                shouldSetNewUsernameManuallyIfOAuth = true
                shouldSetNewUsernameManuallyNoOAuth = false
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN
                uniqueNameExamples = "andstatus@identi.ca AndStatus@datamost.com test425@1realtime.net"
                // This is not a hard limit, just for convenience
                textLimitDefault = TEXT_LIMIT_MAXIMUM
                urlDefault = null
                basicPath = BASIC_PATH_DEFAULT
                oauthPath = OAUTH_PATH_DEFAULT
                originFactory = Function { myContext: MyContext -> OriginPumpio(myContext, this) }
                connectionClass = ConnectionPumpio::class.java
                httpConnectionClassOauth = HttpConnectionOAuthJavaNet::class.java
                httpConnectionClassBasic = HttpConnection::class.java
                isPublicTimeLineSyncable = false
                isSearchTimelineSyncable = false
                isPrivateTimelineSyncable = false
                isInteractionsTimelineSyncable = false
                allowEditing = true
                isPrivateNoteAllowsReply = true
                textMediaTypePosted = TextMediaType.HTML
                textMediaTypeToPost = TextMediaType.HTML
            }
            ApiEnum.ACTIVITYPUB -> {
                isOAuthDefault = true
                canChangeOAuth = false
                originHasUrl = false
                shouldSetNewUsernameManuallyIfOAuth = true
                shouldSetNewUsernameManuallyNoOAuth = false
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN
                uniqueNameExamples = "AndStatus@pleroma.site kaniini@pleroma.site"
                // This is not a hard limit, just for convenience
                textLimitDefault = TEXT_LIMIT_MAXIMUM
                urlDefault = null
                basicPath = BASIC_PATH_DEFAULT
                oauthPath = OAUTH_PATH_DEFAULT
                originFactory = Function { myContext: MyContext -> OriginActivityPub(myContext, this) }
                connectionClass = ConnectionActivityPub::class.java
                httpConnectionClassOauth = HttpConnectionOAuthMastodon::class.java
                httpConnectionClassBasic = HttpConnection::class.java
                isPublicTimeLineSyncable = true
                isSearchTimelineSyncable = false
                isPrivateTimelineSyncable = false
                isInteractionsTimelineSyncable = false
                allowEditing = true
                isPrivateNoteAllowsReply = true
                textMediaTypePosted = TextMediaType.HTML
                textMediaTypeToPost = TextMediaType.HTML
            }
            ApiEnum.GNUSOCIAL_TWITTER -> {
                isOAuthDefault = false
                canChangeOAuth = false
                originHasUrl = true
                shouldSetNewUsernameManuallyIfOAuth = false
                shouldSetNewUsernameManuallyNoOAuth = true
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN
                uniqueNameExamples = "AndStatus@loadaverage.org somebody@gnusocial.no"
                urlDefault = null
                canChangeSsl = true
                basicPath = BASIC_PATH_DEFAULT
                oauthPath = BASIC_PATH_DEFAULT
                originFactory = Function { myContext: MyContext -> OriginGnuSocial(myContext, this) }
                connectionClass = ConnectionTwitterGnuSocial::class.java
                httpConnectionClassOauth = HttpConnectionOAuthApache::class.java
                httpConnectionClassBasic = HttpConnectionBasic::class.java
                isPublicTimeLineSyncable = true
                isSearchTimelineSyncable = true
                isPrivateTimelineSyncable = false
                isInteractionsTimelineSyncable = true
                allowEditing = false
                isPrivateNoteAllowsReply = true
                textMediaTypePosted = TextMediaType.PLAIN
                textMediaTypeToPost = TextMediaType.PLAIN
            }
            ApiEnum.MASTODON -> {
                isOAuthDefault = true
                canChangeOAuth = false
                originHasUrl = true
                shouldSetNewUsernameManuallyIfOAuth = false
                shouldSetNewUsernameManuallyNoOAuth = true
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN
                uniqueNameExamples = "AndStatus@mastodon.social somebody@mstdn.io"
                textLimitDefault = OriginConfig.MASTODON_TEXT_LIMIT_DEFAULT
                urlDefault = null
                basicPath = BASIC_PATH_DEFAULT
                oauthPath = OAUTH_PATH_DEFAULT
                originFactory = Function { myContext: MyContext -> OriginMastodon(myContext, this) }
                connectionClass = ConnectionMastodon::class.java
                httpConnectionClassOauth = HttpConnectionOAuthMastodon::class.java
                httpConnectionClassBasic = HttpConnection::class.java
                isPublicTimeLineSyncable = true
                isSearchTimelineSyncable = true
                isPrivateTimelineSyncable = true
                isInteractionsTimelineSyncable = true
                allowEditing = false
                isPrivateNoteAllowsReply = true
                textMediaTypePosted = TextMediaType.HTML
                textMediaTypeToPost = TextMediaType.PLAIN
            }
            else -> {
                originHasUrl = false
                usernameRegExPattern = Patterns.USERNAME_REGEX_SIMPLE_PATTERN
                urlDefault = null
                basicPath = BASIC_PATH_DEFAULT
                oauthPath = OAUTH_PATH_DEFAULT
                originFactory = Function { myContext: MyContext -> Origin(myContext, this) }
                connectionClass = ConnectionEmpty::class.java
                httpConnectionClassOauth = HttpConnection::class.java
                httpConnectionClassBasic = HttpConnection::class.java
                isPublicTimeLineSyncable = false
                isSearchTimelineSyncable = false
                isPrivateTimelineSyncable = false
                isInteractionsTimelineSyncable = true
                allowEditing = false
                isPrivateNoteAllowsReply = true
                uniqueNameExamples = "userName@hostName.org"
                textMediaTypePosted = TextMediaType.PLAIN
                textMediaTypeToPost = TextMediaType.PLAIN
            }
        }
    }
}
