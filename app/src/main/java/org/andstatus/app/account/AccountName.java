/*
 * Copyright (C) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.StringUtils;

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
    /** The username ("screen name") is unique for the {@link Origin} */
    public final String username;
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

    private static String fixUsername(String usernameIn, Origin origin) {
        String usernameOut = "";
        if (usernameIn != null) {
            usernameOut = usernameIn.trim();
        }
        if (!origin.isUsernameValid(usernameOut)) {
            usernameOut = "";
        }
        return usernameOut;
    }
    
    private static String accountNameToUsername(String accountName, Origin origin) {
        String accountNameFixed = fixAccountName(accountName);
        int indSeparator = accountNameFixed.indexOf(ORIGIN_SEPARATOR);
        String usernameOut = "";
        if (indSeparator > 0) {
            usernameOut = accountNameFixed.substring(0, indSeparator);
        }
        return fixUsername(usernameOut, origin);
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

    protected static AccountName fromOriginAndUserNames(MyContext myContext, String originName,
                                                        String username) {
        return fromOriginAndUsername(myContext.origins().fromName(fixOriginName(originName)), username);
    }

    public static AccountName fromOriginAndUsername(@NonNull Origin origin, String username) {
        return new AccountName(fixUsername(username, origin), origin);
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
        return new AccountName(accountNameToUsername(accountNameString, origin), origin);
    }
    
    private AccountName(String username, Origin origin) {
        this.username = username;
        this.origin = origin;
        isValid = origin.isUsernameValid(username) && origin.isPersistent();
    }

    public String getName() {
        return username + ORIGIN_SEPARATOR + origin.getName();
    }

    @Override
    public String toString() {
        return (isValid ? "" : "(invalid " + usernameToString() + ")") + getName();
    }

    @NonNull
    private String usernameToString() {
        return origin.isUsernameValid(username) ? "" : "username " + origin + " ";
    }

    public Origin getOrigin() {
        return origin;
    }

    public boolean isValid() {
        return isValid;
    }
    
    public String getUsername() {
        return username;
    }

    String getOriginName() {
        return origin.getName();
    }

    public String getLogName() {
        return getName().replace("@", "-").replace(ORIGIN_SEPARATOR, "-");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof AccountName)) return false;

        AccountName that = (AccountName) o;

        if (!origin.equals(that.origin)) return false;
        return StringUtils.equalsNotEmpty(username, that.username);

    }

    @Override
    public int hashCode() {
        int result = origin.hashCode();
        if (!StringUtils.isEmpty(username)) {
            result = 31 * result + username.hashCode();
        }
        return result;
    }
}
