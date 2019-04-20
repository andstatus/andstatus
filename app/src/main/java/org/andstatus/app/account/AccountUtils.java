/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.PeriodicSync;
import android.os.Bundle;

import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import io.vavr.control.Try;

public class AccountUtils {
    public static final int ACCOUNT_VERSION = 48;
    public final static String KEY_VERSION = "myversion"; // Storing version of the account data
    /** The Key for the android.accounts.Account bundle */
    public static final String KEY_ACCOUNT = "account";

    private AccountUtils() {
        // Empty
    }

    public static boolean isVersionCurrent(Context context, Account account) {
        return AccountUtils.ACCOUNT_VERSION == getVersion(context, account);
    }

    public static int getVersion(Context context, Account account) {
        return JsonUtils.toJsonObject(AccountManager.get(context).getUserData(account, KEY_ACCOUNT))
                .map(AccountUtils::getVersion)
                .getOrElse(0);
    }

    public static boolean isVersionCurrent(JSONObject jso) {
        return AccountUtils.ACCOUNT_VERSION == getVersion(jso);
    }

    public static int getVersion(JSONObject jso) {
        return jso == null ? 0 : jso.optInt(KEY_VERSION);
    }

    /** Add this account to the Account Manager Without userdata yet */
    public static Try<Account> addEmptyAccount(AccountName oAccountName, String password) {
        return addEmptyAccount(AccountManager.get(oAccountName.getContext()), oAccountName.getName(), password);
    }

    /** Add this account to the Account Manager Without userdata yet */
    public static Try<Account> addEmptyAccount(AccountManager am, String accountName, String password) {
        return Try.of( () -> {
            Account androidAccount = new Account(accountName, AuthenticatorService.ANDROID_ACCOUNT_TYPE);
            if (am.addAccountExplicitly(androidAccount, password, null)) {
                // Without SyncAdapter we got the error:
                // SyncManager(865): can't find a sync adapter for SyncAdapterType Key
                // {name=org.andstatus.app.data.MyProvider, type=org.andstatus.app},
                // removing settings for it
                MyLog.v(AccountUtils.class, () -> "Persisted " + accountName);
                return androidAccount;
            } else {
                String errorMessage = "Account was not added to AccountManager: " + androidAccount;
                MyLog.e(AccountUtils.class, errorMessage);
                throw new Exception(errorMessage);
            }
        });
    }

    static Try<Account> getExistingAndroidAccount(AccountName oAccountName) {
        for (Account account : getCurrentAccounts(oAccountName.getContext())) {
            if (oAccountName.getName().equals(account.name)) {
                return Try.success(account);
            }
        }
        // Try to find by a short Account name (Legacy...)
        for (Account account : getCurrentAccounts(oAccountName.getContext())) {
            if (oAccountName.getShortName().equals(account.name)) {
                return Try.success(account);
            }
        }
        return Try.failure(new NoSuchElementException());
    }

    private static long getSyncFrequencySeconds(Account account) {
        long syncFrequencySeconds = 0;
        List<PeriodicSync> syncs = ContentResolver.getPeriodicSyncs(account, MatchedUri.AUTHORITY);
        if (!syncs.isEmpty()) {
            syncFrequencySeconds = syncs.get(0).period;
        }
        return syncFrequencySeconds;
    }

    static void setSyncFrequencySeconds(Account androidAccount, long syncFrequencySeconds) {
        // See
        // http://developer.android.com/reference/android/content/ContentResolver.html#addPeriodicSync(android.accounts.Account, java.lang.String, android.os.Bundle, long)
        // and
        // http://stackoverflow.com/questions/11090604/android-syncadapter-automatically-initialize-syncing
        if (syncFrequencySeconds != getSyncFrequencySeconds(androidAccount)) {
            ContentResolver.removePeriodicSync(androidAccount, MatchedUri.AUTHORITY, Bundle.EMPTY);
            if (syncFrequencySeconds > 0) {
                ContentResolver.addPeriodicSync(androidAccount, MatchedUri.AUTHORITY, Bundle.EMPTY, syncFrequencySeconds);
            }
        }
    }

    @NonNull
    public static List<Account> getCurrentAccounts(Context context) {
        return getAllAccounts(context).stream().filter(a -> isVersionCurrent(context, a)).collect(Collectors.toList());
    }

    @NonNull
    public static List<Account> getAllAccounts(Context context) {
        if (Permissions.checkPermission(context, Permissions.PermissionType.GET_ACCOUNTS) ) {
            AccountManager am = AccountManager.get(context);
            return Arrays.stream(am.getAccountsByType(AuthenticatorService.ANDROID_ACCOUNT_TYPE))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
