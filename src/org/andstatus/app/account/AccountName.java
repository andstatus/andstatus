/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;

/**
 * Account name, unique for this application and suitable for {@link android.accounts.AccountManager}
 * The name is permanent and cannot be changed. This is why it may be used as Key to retrieve the account.
 * Immutable class.  
 * @author yvolk@yurivolkov.com
 */
class AccountName {

    /**
     * Prefix of the user's Preferences file
     */
    public static final String FILE_PREFIX = "user_";
    public static final String ORIGIN_SEPARATOR = "/";
    
    /**
     * The system in which the username is defined, see {@link Origin}
     */
    private Origin origin;
    /**
     * The username is unique for the {@link Origin}
     */
    private String username;

    protected static String accountNameToOriginName(String accountName) {
        accountName = AccountName.fixAccountName(accountName);
        int indSeparator = accountName.lastIndexOf(ORIGIN_SEPARATOR);
        String originName = "";
        if (indSeparator >= 0 &&
            indSeparator < accountName.length()-1) {
                originName = accountName.substring(indSeparator + 1);
        }
        return fixOriginName(originName);
    }

    private String fixUsername(String usernameIn) {
        String usernameOut = "";
        if (usernameIn != null) {
            usernameOut = usernameIn.trim();
        }
        if (!origin.isUsernameValid(usernameOut)) {
            usernameOut = "";
        }
        return usernameOut;
    }
    
    String accountNameToUsername(String accountName) {
        accountName = fixAccountName(accountName);
        int indSeparator = accountName.indexOf(ORIGIN_SEPARATOR);
        String usernameOut = "";
        if (indSeparator > 0) {
            usernameOut = accountName.substring(0, indSeparator);
        }
        return fixUsername(usernameOut);
    }

    static String fixAccountName(String accountNameIn) {
        String accountName = "";
        if (accountNameIn != null) {
            accountName = accountNameIn.trim();
        }
        return accountName;
    }

    protected static AccountName getEmpty() {
        AccountName accountName = new AccountName();
        accountName.origin = Origin.getEmpty(OriginType.UNKNOWN);
        accountName.username = "";
        return accountName;
    }

    protected static AccountName fromOriginAndUserNames(String originName, String username) {
        AccountName accountName = new AccountName();
        accountName.origin = MyContextHolder.get().persistentOrigins().fromName(fixOriginName(originName));
        accountName.username = accountName.fixUsername(username);
        return accountName;
    }

    protected static String fixOriginName(String originNameIn) {
        String originName = "";
        if (originNameIn != null) {
            originName = originNameIn.trim();
        }
        return originName;
    }
    
    protected static AccountName fromAccountName(MyContext myContext, String accountNameString) {
        AccountName accountName = new AccountName();
        accountName.origin = myContext.persistentOrigins().fromName(accountNameToOriginName(accountNameString));
        accountName.username = accountName.accountNameToUsername(accountNameString);
        return accountName;
    }
    
    private AccountName() {
    }
    
    @Override
    public String toString() {
        return username + ORIGIN_SEPARATOR + origin.getName();
    }

    Origin getOrigin() {
        return origin;
    }

    boolean isValid() {
        return origin.isUsernameValid(username) && origin.isPersistent();
    }
    
    String getUsername() {
        return username;
    }

    String getOriginName() {
        return origin.getName();
    }

    public int compareTo(String string) {
        return toString().compareTo(string);
    }

    /**
     * Name of preferences file for this MyAccount
     * @return Name without path and extension
     */
    String prefsFileName() {
        return FILE_PREFIX + toString().replace("@", "-").replace(ORIGIN_SEPARATOR, "-");
    }
}
