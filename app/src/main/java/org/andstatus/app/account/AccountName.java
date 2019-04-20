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

import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.StringUtils;

import androidx.annotation.NonNull;

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
    /** The name is a username@originHost to be unique for the {@link OriginType} */
    private final String uniqueName;
    public final boolean isValid;

    private static String accountNameToOriginName(String accountName) {
        String accountNameFixed = AccountName.fixAccountName(accountName);
        int indSeparator = accountNameFixed.lastIndexOf(ORIGIN_SEPARATOR);
        String originName = "";
        if (indSeparator >= 0 &&
            indSeparator < accountNameFixed.length()-1) {
                originName = accountNameFixed.substring(indSeparator + 1);
        }
        return fixOriginName(originName);
    }

    private static String fixUniqueName(String uniqueNameIn, Origin origin) {
        String nonNullName = StringUtils.notNull(uniqueNameIn).trim();
        String uniqueName = nonNullName + (origin.getOriginType().uniqueNameHasHost() && !nonNullName.contains("@")
                    && origin.shouldHaveUrl()
                ? "@" + origin.getHost() : "");
        if (Actor.uniqueNameInOriginToUsername(origin, uniqueName).isPresent()) {
            return uniqueName;
        } else {
            return "";
        }
    }
    
    private static String accountNameToUniqueName(String accountName, Origin origin) {
        String accountNameFixed = fixAccountName(accountName);
        int indSeparator = accountNameFixed.indexOf(ORIGIN_SEPARATOR);
        String usernameOut = indSeparator > 0
            ? accountNameFixed.substring(0, indSeparator)
            : "";
        return fixUniqueName(usernameOut, origin);
    }

    private static String fixAccountName(String accountNameIn) {
        String accountName = "";
        if (accountNameIn != null) {
            accountName = accountNameIn.trim();
        }
        return accountName;
    }

    protected static AccountName getEmpty() {
        return new AccountName("", Origin.EMPTY);
    }

    static AccountName fromOriginNameAndUniqueUserName(MyContext myContext, String originName, String uniqueName) {
        return fromOriginAndUniqueName(myContext.origins().fromName(fixOriginName(originName)), uniqueName);
    }

    public static AccountName fromOriginAndUniqueName(@NonNull Origin origin, String uniqueName) {
        return new AccountName(fixUniqueName(uniqueName, origin), origin);
    }

    private static String fixOriginName(String originNameIn) {
        String originName = "";
        if (originNameIn != null) {
            originName = originNameIn.trim();
        }
        return originName;
    }

    @NonNull
    public static AccountName fromAccountName(MyContext myContext, String accountNameString) {
        Origin origin = myContext.origins().fromName(accountNameToOriginName(accountNameString));
        return new AccountName(accountNameToUniqueName(accountNameString, origin), origin);
    }
    
    private AccountName(String uniqueName, Origin origin) {
        this.uniqueName = uniqueName;
        this.origin = origin;
        isValid = origin.isPersistent()
                && Actor.uniqueNameInOriginToUsername(origin, uniqueName)
                    .map(origin::isUsernameValid).orElse(false);
    }

    public String getName() {
        return uniqueName + ORIGIN_SEPARATOR + origin.getOriginType().getTitle();
    }

    // TODO: delete
    public String getShortName() {
        return getUsername() + ORIGIN_SEPARATOR + origin.getOriginType().getTitle();
    }

    @Override
    public String toString() {
        return (isValid ? "" : "(invalid " + usernameToString() + ")") + getName();
    }

    @NonNull
    private String usernameToString() {
        return origin.isUsernameValid(uniqueName) ? "" : "username " + origin + " ";
    }

    public Context getContext() {
        return origin.myContext.context();
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

    public String getUsername() {
        return Actor.uniqueNameInOriginToUsername(origin, getUniqueName()).orElse("");
    }

    String getOriginName() {
        return origin.getName();
    }

    public String getLogName() {
        return getName().replace("@", "-")
                .replace(ORIGIN_SEPARATOR, "-");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountName)) return false;

        AccountName that = (AccountName) o;

        if (!origin.equals(that.origin)) return false;
        return StringUtils.equalsNotEmpty(uniqueName, that.uniqueName);
    }

    @Override
    public int hashCode() {
        int result = origin.hashCode();
        if (!StringUtils.isEmpty(uniqueName)) {
            result = 31 * result + uniqueName.hashCode();
        }
        return result;
    }
}
