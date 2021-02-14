/*
 * Copyright (C) 2013-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.account

import android.content.Context
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.StringUtil

/**
 * Account name, unique for this application and suitable for [android.accounts.AccountManager]
 * The name is permanent and cannot be changed. This is why it may be used as Key to retrieve the account.
 * Immutable class.
 * @author yvolk@yurivolkov.com
 */
class AccountName private constructor(uniqueName: String?,
                                      /** The system in which the Account is defined, see [Origin]  */
                                      val origin: Origin?) {
    val username: String?
    val host: String?

    /** The name is a username@originHost to be unique for the [OriginType]  */
    private val uniqueName: String?
    private val name: String?
    val isValid: Boolean
    fun getName(): String? {
        return name
    }

    override fun toString(): String {
        return (if (isValid) "" else "(invalid) ") + getName()
    }

    fun getContext(): Context? {
        return myContext().context()
    }

    fun myContext(): MyContext? {
        return getOrigin().myContext
    }

    fun getOrigin(): Origin? {
        return origin
    }

    fun isValid(): Boolean {
        return isValid
    }

    fun getUniqueName(): String? {
        return uniqueName
    }

    fun getOriginName(): String? {
        return origin.getName()
    }

    fun getLogName(): String? {
        return getUniqueName().replace("@", "-")
                .replace(ORIGIN_SEPARATOR, "-")
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is AccountName) return false
        val that = o as AccountName?
        return if (origin != that.origin) false else StringUtil.equalsNotEmpty(uniqueName, that.uniqueName)
    }

    override fun hashCode(): Int {
        var result = origin.hashCode()
        if (!StringUtil.isEmpty(uniqueName)) {
            result = 31 * result + uniqueName.hashCode()
        }
        return result
    }

    companion object {
        val ORIGIN_SEPARATOR: String? = "/"
        fun getEmpty(): AccountName? {
            return AccountName("", Origin.Companion.EMPTY)
        }

        fun fromOriginAndUniqueName(origin: Origin, uniqueName: String?): AccountName {
            return AccountName(fixUniqueName(uniqueName, origin), origin)
        }

        fun fromAccountName(myContext: MyContext?, accountNameString: String?): AccountName {
            val origin = accountNameToOrigin(myContext, accountNameString)
            return AccountName(accountNameToUniqueName(accountNameString, origin), origin)
        }

        private fun fixOriginName(originNameIn: String?): String? {
            var originName = ""
            if (originNameIn != null) {
                originName = originNameIn.trim { it <= ' ' }
            }
            return originName
        }

        private fun accountNameToOrigin(myContext: MyContext?, accountName: String?): Origin? {
            val accountNameFixed = fixAccountName(accountName)
            val host = accountNameToHost(accountNameFixed)
            val indSeparator = accountNameFixed.lastIndexOf(ORIGIN_SEPARATOR)
            val originInAccountName = if (indSeparator >= 0 && indSeparator < accountNameFixed.length - 1) accountNameFixed.substring(indSeparator + 1).trim { it <= ' ' } else ""
            return myContext.origins().fromOriginInAccountNameAndHost(fixOriginName(originInAccountName), host)
        }

        fun accountNameToHost(accountName: String?): String? {
            val nameWithoutOrigin = accountNameWithoutOrigin(accountName)
            val indAt = nameWithoutOrigin.indexOf("@")
            return if (indAt >= 0) nameWithoutOrigin.substring(indAt + 1).trim { it <= ' ' } else ""
        }

        private fun accountNameToUniqueName(accountName: String?, origin: Origin?): String? {
            return fixUniqueName(accountNameWithoutOrigin(accountName), origin)
        }

        private fun accountNameWithoutOrigin(accountName: String?): String? {
            val accountNameFixed = fixAccountName(accountName)
            val indSeparator = accountNameFixed.indexOf(ORIGIN_SEPARATOR)
            return if (indSeparator > 0) accountNameFixed.substring(0, indSeparator) else accountNameFixed
        }

        private fun fixUniqueName(uniqueNameIn: String?, origin: Origin?): String? {
            val nonNullName = StringUtil.notNull(uniqueNameIn).trim { it <= ' ' }
            val uniqueName = nonNullName +
                    if (!nonNullName.contains("@") && origin.shouldHaveUrl()) "@" + origin.getAccountNameHost() else ""
            return if (Actor.Companion.uniqueNameToUsername(origin, uniqueName).isPresent()) {
                uniqueName
            } else {
                ""
            }
        }

        private fun fixAccountName(accountNameIn: String?): String? {
            var accountName = ""
            if (accountNameIn != null) {
                accountName = accountNameIn.trim { it <= ' ' }
            }
            return accountName
        }
    }

    init {
        username = Actor.Companion.uniqueNameToUsername(origin, uniqueName).orElse("")
        host = accountNameToHost(uniqueName)
        this.uniqueName = uniqueName
        val originInAccountName = origin.getOriginInAccountName(host)
        name = uniqueName + ORIGIN_SEPARATOR + originInAccountName
        isValid = origin.isPersistent() && origin.isUsernameValid(username) && StringUtil.nonEmpty(originInAccountName)
    }
}