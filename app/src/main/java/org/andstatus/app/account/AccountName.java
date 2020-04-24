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

package org.andstatus.app.account;

import android.content.Context;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.StringUtil;

/**
 * Account name, unique for this application and suitable for {@link android.accounts.AccountManager}
 * The name is permanent and cannot be changed. This is why it may be used as Key to retrieve the account.
 * Immutable class.  
 * @author yvolk@yurivolkov.com
 */
public class AccountName {
    public static final String ORIGIN_SEPARATOR = "/";
    
    /** The system in which the Account is defined, see {@link Origin} */
    public final Origin origin;
    public final String username;
    public final String host;
    /** The name is a username@originHost to be unique for the {@link OriginType} */
    private final String uniqueName;
    private final String name;
    public final boolean isValid;

    public static AccountName getEmpty() {
        return new AccountName("", Origin.EMPTY);
    }

    @NonNull
    public static AccountName fromOriginAndUniqueName(@NonNull Origin origin, String uniqueName) {
        return new AccountName(fixUniqueName(uniqueName, origin), origin);
    }

    @NonNull
    public static AccountName fromAccountName(MyContext myContext, String accountNameString) {
        Origin origin = accountNameToOrigin(myContext, accountNameString);
        return new AccountName(accountNameToUniqueName(accountNameString, origin), origin);
    }

    private static String fixOriginName(String originNameIn) {
        String originName = "";
        if (originNameIn != null) {
            originName = originNameIn.trim();
        }
        return originName;
    }

    private static Origin accountNameToOrigin(MyContext myContext, String accountName) {
        String accountNameFixed = fixAccountName(accountName);
        String host = accountNameToHost(accountNameFixed);
        int indSeparator = accountNameFixed.lastIndexOf(ORIGIN_SEPARATOR);
        String originInAccountName = indSeparator >= 0 && indSeparator < accountNameFixed.length()-1
                ? accountNameFixed.substring(indSeparator + 1).trim()
                : "";

        return myContext.origins().fromOriginInAccountNameAndHost(fixOriginName(originInAccountName), host);
    }


    public static String accountNameToHost(String accountName) {
        String nameWithoutOrigin = accountNameWithoutOrigin(accountName);
        int indAt = nameWithoutOrigin.indexOf("@");
        return indAt >= 0
                ? nameWithoutOrigin.substring(indAt + 1).trim()
                : "";
    }

    private static String accountNameToUniqueName(String accountName, Origin origin) {
        return fixUniqueName(accountNameWithoutOrigin(accountName), origin);
    }

    private static String accountNameWithoutOrigin(String accountName) {
        String accountNameFixed = fixAccountName(accountName);
        int indSeparator = accountNameFixed.indexOf(ORIGIN_SEPARATOR);
        return indSeparator > 0
                ? accountNameFixed.substring(0, indSeparator)
                : accountNameFixed;
    }

    private static String fixUniqueName(String uniqueNameIn, Origin origin) {
        String nonNullName = StringUtil.notNull(uniqueNameIn).trim();
        String uniqueName = nonNullName +
                (!nonNullName.contains("@") && origin.shouldHaveUrl() ? "@" + origin.getAccountNameHost() : "");
        if (Actor.uniqueNameToUsername(origin, uniqueName).isPresent()) {
            return uniqueName;
        } else {
            return "";
        }
    }

    private static String fixAccountName(String accountNameIn) {
        String accountName = "";
        if (accountNameIn != null) {
            accountName = accountNameIn.trim();
        }
        return accountName;
    }

    private AccountName(String uniqueName, Origin origin) {
        this.origin = origin;
        username = Actor.uniqueNameToUsername(origin, uniqueName).orElse("");
        host = accountNameToHost(uniqueName);
        this.uniqueName = uniqueName;
        String originInAccountName = origin.getOriginInAccountName(host);
        name = uniqueName + ORIGIN_SEPARATOR + originInAccountName;
        isValid = origin.isPersistent() && origin.isUsernameValid(username) && StringUtil.nonEmpty(originInAccountName);
    }

    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String toString() {
        return (isValid ? "" : "(invalid) ") + getName();
    }

    public Context getContext() {
        return myContext().context();
    }

    public MyContext myContext() {
        return getOrigin().myContext;
    }

    public Origin getOrigin() {
        return origin;
    }

    public boolean isValid() {
        return isValid;
    }
    
    public String getUniqueName() {
        return uniqueName;
    }

    String getOriginName() {
        return origin.getName();
    }

    public String getLogName() {
        return getUniqueName().replace("@", "-")
                .replace(ORIGIN_SEPARATOR, "-");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountName)) return false;

        AccountName that = (AccountName) o;

        if (!origin.equals(that.origin)) return false;
        return StringUtil.equalsNotEmpty(uniqueName, that.uniqueName);
    }

    @Override
    public int hashCode() {
        int result = origin.hashCode();
        if (!StringUtil.isEmpty(uniqueName)) {
            result = 31 * result + uniqueName.hashCode();
        }
        return result;
    }
}
