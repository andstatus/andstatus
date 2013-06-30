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


/**
 * Account name, unique for this application and suitable for {@link android.accounts.AccountManager}
 * The name is permanent and cannot be changed. This is why it may be used as Key to retrieve the account.
 * Immutable class.  
 * @author yvolk
 */
class AccountName {

    /**
     * Prefix of the user's Preferences file
     */
    public static final String FILE_PREFIX = "user_";
    
    /**
     * The system in which the username is defined, see {@link Origin}
     */
    private Origin origin;
    private String username;

    static String accountNameToOriginName(String accountName) {
        accountName = AccountName.fixAccountName(accountName);
        int indSlash = accountName.indexOf("/");
        String originName = "";
        if (indSlash >= 0) {
            originName = accountName.substring(0, indSlash);
        }
        return originName;
    }

    static String accountNameToUsername(String accountName) {
        accountName = AccountName.fixAccountName(accountName);
        int indSlash = accountName.indexOf("/");
        String username = "";
        if (indSlash >= 0) {
            if (indSlash < accountName.length()-1) {
                username = accountName.substring(indSlash + 1);
            }
        } else {
            username = accountName;
        }
        return UserNameUtil.fixUsername(username);
    }

    static String fixAccountName(String accountName_in) {
        String accountName = "";
        if (accountName_in != null) {
            accountName = accountName_in.trim();
        }
        return accountName;
    }

    static AccountName fromOriginAndUserNames(String originName, String username) {
        AccountName accountName = new AccountName();
        accountName.origin = Origin.toExistingOrigin(originName);
        accountName.username = UserNameUtil.fixUsername(username);
        return accountName;
    }

    static AccountName fromAccountName(String accountNameString) {
        AccountName accountName = new AccountName();
        accountName.origin = Origin.toExistingOrigin(accountNameToOriginName(accountNameString));
        accountName.username = accountNameToUsername(accountNameString);
        return accountName;
    }
    
    private AccountName() {};
    
    @Override
    public String toString() {
        return origin.getName() + "/" + username;
    }

    Origin getOrigin() {
        return origin;
    }

    String getUsername() {
        return username;
    }

    public int compareTo(String string) {
        return toString().compareTo(string);
    }

    /**
     * Name of preferences file for this MyAccount
     * @return Name without path and extension
     */
    String prefsFileName() {
        String fileName = FILE_PREFIX + toString().replace("/", "-");
        return fileName;
    }
    
}
