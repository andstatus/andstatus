/*
 * Copyright (C) 2010-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.SearchObjects;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.converter.DatabaseConverterController;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.OAuthService;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConfig;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineSaver;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.json.JSONObject;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import io.vavr.control.Try;

/**
 * Immutable class that holds "AndStatus account"-specific information including: 
 * a Social network (twitter.com, identi.ca etc.),
 * Username in that system and {@link Connection} to it.
 *
 * @author yvolk@yurivolkov.com
 */
public final class MyAccount implements Comparable<MyAccount>, IsEmpty {
    private static final String TAG = MyAccount.class.getSimpleName();
    public static final MyAccount EMPTY = Builder.getEmptyAccount(MyContext.EMPTY,"(empty)");

    public static final String KEY_ACCOUNT_NAME = "account_name";
    /** Username for the account */
    public static final String KEY_USERNAME = "username";
    /** A name that is unique for an origin */
    public static final String KEY_UNIQUE_NAME = "unique_name";
    /** {@link ActorTable#_ID} in our System. */
    public static final String KEY_ACTOR_ID = "user_id";
    /** {@link ActorTable#ACTOR_OID} in Microblogging System. */
    public static final String KEY_ACTOR_OID = "user_oid";
    /** Is OAuth on for this MyAccount? */
    public static final String KEY_OAUTH = "oauth";
    /** This account is in the process of deletion and should be ignored... */
    public static final String KEY_DELETED = "deleted";
    /** @see android.content.ContentResolver#getIsSyncable(Account, String) */
    public static final String KEY_IS_SYNCABLE = "is_syncable";
    /** This corresponds to turning syncing on/off in Android Accounts
     * @see android.content.ContentResolver#getSyncAutomatically(Account, String) */
    public static final String KEY_IS_SYNCED_AUTOMATICALLY = "sync_automatically";
    public static final String KEY_ORDER = "order";

    private final MyContext myContext;
    final AccountData accountData;
    private AccountName oAccountName;
    private Actor actor;

    private volatile Connection connection = null;
    /** Was this account authenticated last time _current_ credentials were verified?
     *  CredentialsVerified.NEVER - after changes of "credentials": password/OAuth...
     */
    private CredentialsVerificationStatus credentialsVerified = CredentialsVerificationStatus.NEVER;
    /** Is this account authenticated with OAuth? */
    private boolean isOAuth = true;
    private long syncFrequencySeconds = 0;
    boolean isSyncable = true;
    private boolean isSyncedAutomatically = true;
    private boolean deleted;
    private int order = 0;

    public static MyAccount getEmpty(MyContext myContext, String accountName) {
        return Builder.getEmptyAccount(myContext, accountName);
    }

    public static MyAccount fromBundle(MyContext myContext, Bundle bundle) {
        return bundle == null
                ? EMPTY
                : myContext.accounts().fromAccountName(bundle.getString(IntentExtra.ACCOUNT_NAME.key));
    }

    private MyAccount(MyContext myContext, AccountData accountDataIn, String accountName) {
        this.myContext = myContext;
        this.accountData = accountDataIn == null
                ? AccountData.fromJson(null, false)
                : accountDataIn;
        oAccountName = accountDataIn == null
                ? AccountName.fromAccountName(myContext, accountName)
                : AccountName.fromOriginNameAndUniqueName(myContext,
                accountDataIn.getDataString(Origin.KEY_ORIGIN_NAME, ""),
                accountDataIn.getDataString(KEY_UNIQUE_NAME, ""));
        actor = Actor.load(myContext, accountData.getDataLong(KEY_ACTOR_ID, 0L), false, () ->
                Actor.fromOid(oAccountName.getOrigin(), accountData.getDataString(KEY_ACTOR_OID, ""))
                        .withUniqueName(oAccountName.getUniqueName())
                        .lookupUser());

        deleted = accountData.getDataBoolean(KEY_DELETED, false);
        syncFrequencySeconds = accountData.getDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, 0L);
        isSyncable = accountData.getDataBoolean(KEY_IS_SYNCABLE, true);
        isSyncedAutomatically = accountData.getDataBoolean(KEY_IS_SYNCED_AUTOMATICALLY, true);
        setOAuth(TriState.UNKNOWN);
        credentialsVerified = CredentialsVerificationStatus.load(accountData);
        order = accountData.getDataInt(KEY_ORDER, 1);
    }

    public AccountName getOAccountName() {
        return oAccountName;
    }

    public Actor getActor() {
        return Actor.load(myContext, actor.actorId, false, () -> actor);
    }

    public String getWebFingerId() {
        return actor.getWebFingerId();
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY;
    }

    public Connection setConnection() {
        connection = Connection.fromMyAccount(this, TriState.fromBoolean(isOAuth));
        return connection;
    }

    private Try<Account> getNewOrExistingAndroidAccount() {
        return AccountUtils.getExistingAndroidAccount(oAccountName).recoverWith(Exception.class,
                notFound -> isValidAndSucceeded()
                    ? AccountUtils.addEmptyAccount(oAccountName, getPassword())
                    : Try.failure(notFound));
    }

    public boolean getCredentialsPresent() {
        return getConnection() != null && getConnection().getCredentialsPresent();
    }

    public CredentialsVerificationStatus getCredentialsVerified() {
        return credentialsVerified;
    }

    public boolean isValidAndSucceeded() {
        return isValid() && getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED;
    }

    private boolean isPersistent() {
        return accountData.isPersistent();
    }

    public boolean isFollowing(MyContext myContext, Actor thatActor) {
        return myContext.users().friendsOfMyActors.entrySet().stream()
                .filter(entry -> entry.getKey() == thatActor.actorId)
                .anyMatch(entry -> entry.getValue().contains(getActor().actorId));
    }

    /**
     * Are authenticated users from more than one different Originating system?
     * @return count
     * @param myContext
     */
    public String getShortestUniqueAccountName(MyContext myContext) {
        String uniqueName = getAccountName();

        boolean found = false;
        String possiblyUnique = getUsername();
        for (MyAccount persistentAccount : myContext.accounts().get()) {
            if (!persistentAccount.toString().equalsIgnoreCase(toString())
                    && persistentAccount.getUsername().equalsIgnoreCase(possiblyUnique)) {
                found = true;
                break;
            }
        }
        if (!found) {
            uniqueName = possiblyUnique;
            int indAt = uniqueName.indexOf('@');
            if (indAt > 0) {
                possiblyUnique = uniqueName.substring(0, indAt);
                for (MyAccount persistentAccount : myContext.accounts().get()) {
                    if (!persistentAccount.toString().equalsIgnoreCase(toString())) {
                        String toCompareWith = persistentAccount.getUsername();
                        indAt = toCompareWith.indexOf('@');
                        if (indAt > 0) {
                            toCompareWith = toCompareWith.substring(0, indAt);
                        }
                        if (toCompareWith.equalsIgnoreCase(possiblyUnique)) {
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    uniqueName = possiblyUnique;
                }
            }
        }
        return uniqueName;
    }

    public boolean nonValid() {
        return !isValid();
    }

    public boolean isValid() {
        return !deleted
                && actor.actorId != 0
                && connection != null
                && oAccountName.isValid
                && !StringUtils.isEmpty(actor.oid);
    }

    private void setOAuth(TriState isOAuthTriState) {
        Origin origin = oAccountName.getOrigin();
        boolean isOAuthBoolean = true;
        if (isOAuthTriState == TriState.UNKNOWN) {
            isOAuthBoolean = accountData.getDataBoolean(KEY_OAUTH, origin.isOAuthDefault());
        } else {
            isOAuthBoolean = isOAuthTriState.toBoolean(origin.isOAuthDefault());
        }
        isOAuth = origin.getOriginType().fixIsOAuth(isOAuthBoolean);
    }

    public String getUsername() {
        return actor.getUsername();
    }

    /**
     * @return account name, unique for this application and suitable for android.accounts.AccountManager
     * The name is permanent and cannot be changed. This is why it may be used as Id 
     */
    public String getAccountName() {
        return oAccountName.getName();
    }

    public long getActorId() {
        return actor.actorId;
    }

    public String getActorOid() {
        return actor.oid;
    }

    /**
     * @return The system in which the Account is defined, see {@link OriginTable}
     */
    public Origin getOrigin() {
        return actor.origin;
    }

    public long getOriginId() {
        return actor.origin.getId();
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean areClientKeysPresent() {
        return connection.areOAuthClientKeysPresent();
    }

    public OAuthService getOAuthService() {
        return connection.getOAuthService();
    }

    public int getOrder() {
        return order;
    }

    public int charactersLeftForNote(String html) {
        return oAccountName.getOrigin().charactersLeftForNote(html);
    }

    public int alternativeTermForResourceId(int resId) {
        return oAccountName.getOrigin().alternativeTermForResourceId(resId);
    }

    public boolean isOAuth() {
        return isOAuth;
    }

    public String getPassword() {
        return getConnection().getPassword();
    }

    public boolean isUsernameNeededToStartAddingNewAccount() {
        return oAccountName.getOrigin().getOriginType().isUsernameNeededToStartAddingNewAccount(isOAuth());
    }

    public boolean isUsernameValid() {
        return actor.isUsernameValid();
    }

    public boolean isSearchSupported(SearchObjects searchObjects) {
        return getConnection().hasApiEndpoint(searchObjects == SearchObjects.NOTES
                ? ApiRoutineEnum.SEARCH_NOTES : ApiRoutineEnum.SEARCH_ACTORS);
    }

    public void requestSync() {
        if (!isPersistent()) return;
        AccountUtils.getExistingAndroidAccount(oAccountName).onSuccess(a -> ContentResolver.requestSync(a, MatchedUri.AUTHORITY, new Bundle()));
    }

    public long getSyncFrequencySeconds() {
        return syncFrequencySeconds;
    }

    public long getEffectiveSyncFrequencyMillis() {
        long effectiveSyncFrequencySeconds = getSyncFrequencySeconds();
        if (effectiveSyncFrequencySeconds <= 0) {
            effectiveSyncFrequencySeconds= MyPreferences.getSyncFrequencySeconds();
        }
        return TimeUnit.SECONDS.toMillis(effectiveSyncFrequencySeconds);
    }

    @Override
    public String toString() {
        if (EMPTY == this) {
            return MyLog.formatKeyValue(TAG, "EMPTY");
        }

        String members = (isValid() ? "" : "(invalid) ") + "accountName:" + oAccountName + ",";
        try {
            if (actor != null && actor.nonEmpty()) {
                members += actor + ",";
            }
            if (!isPersistent()) {
                members += "not persistent,";
            }
            if (isOAuth()) {
                members += "OAuth,";
            }
            if (getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
                members += "verified:" + getCredentialsVerified().name() + ",";
            }
            if (getCredentialsPresent()) {
                members += "credentialsPresent,";
            }
            if (connection == null) {
                members += "connection:null,";
            }
            if (syncFrequencySeconds > 0) {
                members += "syncFrequency:" + syncFrequencySeconds + ",";
            }
            if (isSyncable) {
                members += "syncable,";
            }
            if (isSyncedAutomatically) {
                members += "syncauto,";
            }
            if (deleted) {
                members += "deleted,";
            }
        } catch (Exception e) {
            MyLog.v(this, members, e);
        }
        return MyLog.formatKeyValue(this, members);
    }
    
    public JSONObject toJson() {
        return accountData.updateFrom(this).toJSon();
    }

    public String toAccountButtonText(MyContext myContext) {
        String accountButtonText = getShortestUniqueAccountName(myContext);
        if (!isValidAndSucceeded()) {
            accountButtonText = "(" + accountButtonText + ")";
        }
        return accountButtonText;
    }

    @Override
    public int compareTo(MyAccount another) {
        if (this == another) {
            return 0;
        }
        if (another == null) {
            return -1;
        }
        if (isValid() != another.isValid()) {
            return isValid() ? -1 : 1;
        }
        return order > another.order
            ? 1
            : (order < another.order
                ? -1
                : getAccountName().compareTo(another.getAccountName()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MyAccount)) return false;

        MyAccount myAccount = (MyAccount) o;
        if (!oAccountName.equals(myAccount.oAccountName)) return false;
        return StringUtils.equalsNotEmpty(actor.oid, myAccount.actor.oid);
    }

    @Override
    public int hashCode() {
        int result = oAccountName.hashCode();
        if (!StringUtils.isEmpty(actor.oid)) {
            result = 31 * result + actor.oid.hashCode();
        }
        return result;
    }

    public boolean shouldBeSyncedAutomatically() {
        return isSyncedAutomatically() && isValidAndSucceeded() && getEffectiveSyncFrequencyMillis() > 0;
    }

    public boolean isSyncedAutomatically() {
        return isSyncedAutomatically;
    }

    public long getLastSyncSucceededDate(MyContext myContext) {
        return (isValid() && isPersistent())
                ? myContext.timelines()
                    .filter(false, TriState.UNKNOWN, TimelineType.UNKNOWN, actor, Origin.EMPTY)
                    .map(Timeline::getSyncSucceededDate).max(Long::compareTo).orElse(0L)
                : 0L;
    }

    public boolean hasAnyTimelines(MyContext myContext) {
        for (Timeline timeline : myContext.timelines().values()) {
            if (timeline.myAccountToSync.equals(this)) {
                return true;
            }
        }
        MyLog.v(this, () -> this.getAccountName() + " doesn't have any timeline");
        return false;
    }

    /** Companion class used to load/create/change/delete {@link MyAccount}'s data */
    public static final class Builder implements Parcelable {
        private static final String TAG = MyAccount.TAG + "." + Builder.class.getSimpleName();

        public final MyContext myContext;
        private volatile MyAccount myAccount;

        /**
         * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
         */
        public static Builder newOrExistingFromAccountName(MyContext myContext, String accountName, TriState isOAuthTriState) {
            MyAccount persistentAccount = myContext.accounts().fromAccountName(accountName);
            if (persistentAccount.isValid()) {
                return fromMyAccount(myContext, persistentAccount, "newOrExistingFromAccountName");
            } else {
                return newFromAccountName(myContext, accountName, isOAuthTriState);
            }
        }

        private static MyAccount getEmptyAccount(MyContext myContext, String accountName) {
            return newFromAccountName(myContext, accountName, TriState.UNKNOWN).getAccount();
        }

        /** Creates new account, which is not Persistent yet */
        private static Builder newFromAccountName(MyContext myContext, String accountName, TriState isOAuthTriState) {
            MyAccount ma = new MyAccount(myContext, null, accountName);
            ma.setOAuth(isOAuthTriState);
            return new Builder(myContext, ma);
        }

        /** Loads existing account from Persistence */
        static Builder fromAndroidAccount(MyContext myContext, @NonNull android.accounts.Account account) {
            return fromAccountData(myContext, AccountData.fromAndroidAccount(myContext.context(), account),
                    "fromAndroidAccount");
        }

        static Builder fromAccountData(MyContext myContext, AccountData accountData, String method) {
            return fromMyAccount(myContext, new MyAccount(myContext, accountData, ""), method);
        }

        static Builder fromMyAccount(MyContext myContext, MyAccount ma, String method) {
            Builder builder = new Builder(myContext, ma);
            if (!MyContextHolder.isOnRestore()) builder.fixInconsistenciesWithChangedEnvironmentSilently();
            builder.logLoadResult(method);
            return builder;
        }

        private Builder(MyContext myContext, MyAccount myAccount) {
            myAccount.setConnection();
            this.myContext = myContext;
            this.myAccount = myAccount;
        }

        private void fixInconsistenciesWithChangedEnvironmentSilently() {
            boolean changed = false;
            if (isPersistent() && myAccount.actor.actorId == 0) {
                changed = true;
                assignActorId();
                MyLog.e(TAG, "MyAccount '" + myAccount.getAccountName()
                        + "' was not connected to the Actor table. actorId=" + myAccount.actor.actorId);
            }
            if (!myAccount.getCredentialsPresent()
                    && myAccount.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                MyLog.e(TAG, "Account's credentials were lost?! Fixing...");
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER);
                changed = true;
            }
            if (changed && isPersistent()) {
                saveSilently();
            }
        }

        private void logLoadResult(String method) {
            if (myAccount.isValid()) {
                MyLog.v(TAG, () -> method + " Loaded " + this.toString());
            } else {
                MyLog.i(TAG, method + " Failed to load: Invalid account; " + this);
            }
        }

        public MyAccount getAccount() {
            return myAccount;
        }

        /**
         * @return Is this object persistent
         */
        public boolean isPersistent() {
            return myAccount.isPersistent();
        }

        /**
         * Delete all Account's data
         * @return true = success
         */
        boolean deleteData() {
            boolean ok = true;

            if (isPersistent() && myAccount.actor.actorId != 0) {
                // TODO: Delete data for this Account ?!
                myAccount.actor.actorId = 0;
            }
            setAndroidAccountDeleted();
            return ok;
        }

        private void setAndroidAccountDeleted() {
            myAccount.accountData.setDataBoolean(KEY_DELETED, true);
        }

        public void setSyncedAutomatically(boolean syncedAutomatically) {
            myAccount.isSyncedAutomatically = syncedAutomatically;
        }

        public void setOrder(int order) {
            this.myAccount.order = order;
        }

        void save() {
            if (saveSilently().getOrElse(false) && myContext.isReady()) {
                MyPreferences.onPreferencesChanged();
            }
        }

        /** Save this MyAccount to AccountManager */
        Try<Boolean> saveSilently() {
            if (myAccount.isValid()) {
                return myAccount.getNewOrExistingAndroidAccount()
                        .onSuccess(account -> myAccount.accountData.updateFrom(myAccount))
                        .flatMap(account -> myAccount.accountData.saveIfChanged(myContext.context(), account))
                        .onFailure(e -> myAccount.accountData.setPersistent(false))
                        .onSuccess(result1 -> {
                            MyLog.v(this, () -> (result1 ? " Saved " : " Didn't change ") +
                                    this.toString());
                            myContext.accounts().addIfAbsent(myAccount);
                            if (myContext.isReady() && !myAccount.hasAnyTimelines(myContext)) {
                                new TimelineSaver(myContext).setAddDefaults(true).setAccount(myAccount).executeNotOnUiThread();
                            }
                        })
                        .onFailure(e -> MyLog.v(this, () -> "Failed to save" + this.toString() +
                                "; Error: " + e.getMessage()));
            } else {
                MyLog.v(TAG, () -> "Didn't save invalid account: " + myAccount);
                return Try.failure(new Exception());
            }
        }

        void getOriginConfig() throws ConnectionException {
            OriginConfig config = myAccount.getConnection().getConfig();
            if (config.nonEmpty()) {
                Origin.Builder originBuilder = new Origin.Builder(myAccount.getOrigin());
                originBuilder.save(config);
                MyLog.v(this, "Get Origin config succeeded");
            }
        }

        /**
         * Verify the account's credentials. Returns true if authentication was
         * successful
         *
         * @see CredentialsVerificationStatus
         */
        void verifyCredentials(Optional<Uri> whoAmI) throws ConnectionException {
            try {
                onCredentialsVerified(myAccount.getConnection().verifyCredentials(whoAmI), null);
            } catch (ConnectionException e) {
                onCredentialsVerified(Actor.EMPTY, e);
            }
        }

        public void onCredentialsVerified(@NonNull Actor actor, ConnectionException e) throws ConnectionException {
            boolean ok = e == null && !actor.isEmpty() && StringUtils.nonEmpty(actor.oid)
                    && actor.isUsernameValid();
            boolean errorSettingUsername = !ok;

            boolean credentialsOfOtherAccount = false;
            // We are comparing usernames ignoring case, but we fix correct case
            // as the Originating system tells us.
            if (ok && !StringUtils.isEmpty(myAccount.getUsername())
                    && myAccount.oAccountName.username.compareToIgnoreCase(actor.getUsername()) != 0) {
                // Credentials belong to other Account ??
                ok = false;
                credentialsOfOtherAccount = true;
            }

            if (ok) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED);
                actor.lookupActorId();
                actor.lookupUser();
                actor.user.setIsMyUser(TriState.TRUE);
                actor.setUpdatedDate(MyLog.uniqueCurrentTimeMS());
                myAccount.actor = actor;
                if (DatabaseConverterController.isUpgrading()) {
                    MyLog.v(TAG, "Upgrade in progress");
                    myAccount.actor.actorId = myAccount.accountData.getDataLong(KEY_ACTOR_ID, myAccount.actor.actorId);
                } else {
                    new DataUpdater(myAccount).onActivity(actor.update(actor));
                }
                if (!isPersistent()) {
                    // Now we know the name (or proper case of the name) of this Account!
                    // We don't recreate MyAccount object for the new name
                    //   in order to preserve credentials.
                    myAccount.oAccountName = AccountName.fromOriginAndUniqueName(
                            myAccount.oAccountName.getOrigin(), actor.getUniqueName());
                    myAccount.connection.save(myAccount.accountData);
                    myAccount.setConnection();
                    save();
                }
            }
            if (!ok || !myAccount.getCredentialsPresent()) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED);
            }
            save();

            if (e != null) {
                throw e;
            }
            if (credentialsOfOtherAccount) {
                MyLog.e(TAG, myContext.context().getText(R.string.error_credentials_of_other_user) + ": " +
                        actor.getUniqueNameWithOrigin() +
                        " account name: " + myAccount.oAccountName.getName() +
                        " vs username: " + actor.getUsername());
                throw new ConnectionException(StatusCode.CREDENTIALS_OF_OTHER_ACCOUNT, actor.getUniqueNameWithOrigin());
            }
            if (errorSettingUsername) {
                String msg = myContext.context().getText(R.string.error_set_username) + " " + actor.getUsername();
                MyLog.e(TAG, msg);
                throw new ConnectionException(StatusCode.AUTHENTICATION_ERROR, msg);
            }
        }

        public void setUserTokenWithSecret(String token, String secret) {
            myAccount.getConnection().setUserTokenWithSecret(token, secret);
        }

        public void setCredentialsVerificationStatus(CredentialsVerificationStatus cv) {
            myAccount.credentialsVerified = cv;
            if (cv != CredentialsVerificationStatus.SUCCEEDED
                    && myAccount.connection != null) {
                myAccount.connection.clearAuthInformation();
            }
        }

        public void registerClient() throws ConnectionException {
            MyLog.v(TAG, () -> "Registering client application for " + myAccount.getUsername());
            myAccount.setConnection();
            myAccount.connection.registerClientForAccount();
        }

        /**
         * Password was moved to the connection object because it is needed there
         *
         * @param password
         */
        public void setPassword(String password) {
            if (password.compareTo(myAccount.getConnection().getPassword()) != 0) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER);
                myAccount.getConnection().setPassword(password);
            }
        }

        private void assignActorId() {
            myAccount.actor.actorId = MyQuery.usernameToId(myAccount.getOriginId(), myAccount.getUsername());
            if (myAccount.actor.actorId == 0) {
                DataUpdater di = new DataUpdater(myAccount);
                try {
                    di.onActivity(myAccount.actor.update(myAccount.actor));
                } catch (Exception e) {
                    MyLog.e(TAG, "assignUserId", e);
                }
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            save();
            dest.writeParcelable(myAccount.accountData, flags);
        }

        public static final Creator<Builder> CREATOR = new Creator<Builder>() {
            @Override
            public Builder createFromParcel(Parcel source) {
                return fromAccountData(MyContextHolder.get(), AccountData.fromBundle(MyContextHolder.get().context(),
                        source.readBundle()), "createFromParcel");
            }

            @Override
            public Builder[] newArray(int size) {
                return new Builder[size];
            }
        };

        @Override
        public String toString() {
            return myAccount.toString();
        }

        public void clearClientKeys() {
            myAccount.connection.clearClientKeys();
        }

        void setSyncFrequencySeconds(long syncFrequencySeconds) {
            myAccount.syncFrequencySeconds = syncFrequencySeconds;
        }
    }
}
