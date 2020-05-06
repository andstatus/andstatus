/*
 * Copyright (C) 2010-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.SearchObjects;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.converter.DatabaseConverterController;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.OriginTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.OAuthService;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.ConnectionEmpty;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineSaver;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

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
    public static final MyAccount EMPTY = new MyAccount(AccountName.getEmpty());

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

    @NonNull
    final AccountData data;
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

    public static MyAccount fromBundle(MyContext myContext, Bundle bundle) {
        return bundle == null
                ? EMPTY
                : myContext.accounts().fromAccountName(bundle.getString(IntentExtra.ACCOUNT_NAME.key));
    }

    MyAccount(AccountName accountName) {
        this(AccountData.fromAccountName(accountName));
    }

    MyAccount(@NonNull AccountData accountDataIn) {
        this.data = accountDataIn;
        actor = Actor.load(data.myContext(), data.getDataLong(KEY_ACTOR_ID, 0L), false,
                () -> Actor.fromOid(data.accountName.getOrigin(), data.getDataString(KEY_ACTOR_OID))
                        .withUniqueName(data.accountName.getUniqueName())
                        .lookupUser());
        deleted = data.getDataBoolean(KEY_DELETED, false);
        syncFrequencySeconds = data.getDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, 0L);
        isSyncable = data.getDataBoolean(KEY_IS_SYNCABLE, true);
        isSyncedAutomatically = data.getDataBoolean(KEY_IS_SYNCED_AUTOMATICALLY, true);
        setOAuth(TriState.fromBoolean(data.getDataBoolean(KEY_OAUTH, getOrigin().isOAuthDefault())));
        setConnection();
        getConnection().setPassword(data.getDataString(Connection.KEY_PASSWORD));
        credentialsVerified = CredentialsVerificationStatus.load(data);
        order = data.getDataInt(KEY_ORDER, 1);
    }

    public MyAccount getValidOrCurrent(MyContext myContext) {
        return isValid()
                ? this
                : myContext.accounts().getCurrentAccount();
    }

    public AccountName getOAccountName() {
        return data.accountName;
    }

    public Actor getActor() {
        return Actor.load(data.myContext(), actor.actorId, false, () -> actor);
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
        return AccountUtils.getExistingAndroidAccount(data.accountName).recoverWith(Exception.class,
                notFound -> isValidAndSucceeded()
                    ? AccountUtils.addEmptyAccount(data.accountName, getPassword())
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
        return data.isPersistent();
    }

    public boolean isFollowing(Actor thatActor) {
        return data.myContext().users().friendsOfMyActors.entrySet().stream()
                .filter(entry -> entry.getKey() == thatActor.actorId)
                .anyMatch(entry -> entry.getValue().contains(getActor().actorId));
    }

    public String getShortestUniqueAccountName() {
        String uniqueName = getAccountName();

        boolean found = false;

        String possiblyUnique = getActor().getUniqueName();
        for (MyAccount persistentAccount : data.myContext().accounts().get()) {
            if (!persistentAccount.toString().equalsIgnoreCase(toString())
                    && persistentAccount.getActor().getUniqueName().equalsIgnoreCase(possiblyUnique)) {
                found = true;
                break;
            }
        }
        if (!found) {
            uniqueName = possiblyUnique;
        }

        if (!found) {
            possiblyUnique = getUsername();
            for (MyAccount persistentAccount : data.myContext().accounts().get()) {
                if (!persistentAccount.toString().equalsIgnoreCase(toString())
                        && persistentAccount.getUsername().equalsIgnoreCase(possiblyUnique)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                uniqueName = possiblyUnique;
            }
        }

        if (!found) {
            int indAt = uniqueName.indexOf('@');
            if (indAt > 0) {
                possiblyUnique = uniqueName.substring(0, indAt);
                for (MyAccount persistentAccount : data.myContext().accounts().get()) {
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
                && data.accountName.isValid
                && !StringUtil.isEmpty(actor.oid);
    }

    private void setOAuth(TriState isOAuthTriState) {
        boolean isOAuthBoolean = true;
        if (isOAuthTriState == TriState.UNKNOWN) {
            isOAuthBoolean = data.getDataBoolean(KEY_OAUTH, getOrigin().isOAuthDefault());
        } else {
            isOAuthBoolean = isOAuthTriState.toBoolean(getOrigin().isOAuthDefault());
        }
        isOAuth = getOrigin().getOriginType().fixIsOAuth(isOAuthBoolean);
    }

    public String getUsername() {
        return actor.getUsername();
    }

    /**
     * @return account name, unique for this application and suitable for android.accounts.AccountManager
     * The name is permanent and cannot be changed. This is why it may be used as Id 
     */
    public String getAccountName() {
        return data.accountName.getName();
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
        return data.accountName.origin;
    }

    public long getOriginId() {
        return getOrigin().getId();
    }

    public Connection getConnection() {
        return connection == null ? ConnectionEmpty.EMPTY : connection;
    }

    public boolean areClientKeysPresent() {
        return getConnection().areOAuthClientKeysPresent();
    }

    public OAuthService getOAuthService() {
        return getConnection().getOAuthService();
    }

    public int getOrder() {
        return order;
    }

    public int charactersLeftForNote(String html) {
        return getOrigin().charactersLeftForNote(html);
    }

    public int alternativeTermForResourceId(int resId) {
        return getOrigin().alternativeTermForResourceId(resId);
    }

    public boolean isOAuth() {
        return isOAuth;
    }

    public String getPassword() {
        return getConnection().getPassword();
    }

    public boolean isUsernameNeededToStartAddingNewAccount() {
        return getOrigin().getOriginType().isUsernameNeededToStartAddingNewAccount(isOAuth());
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
        AccountUtils.getExistingAndroidAccount(data.accountName)
                .onSuccess(a -> ContentResolver.requestSync(a, MatchedUri.AUTHORITY, new Bundle()));
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
            return MyStringBuilder.formatKeyValue(TAG, "EMPTY");
        }

        String members = (isValid() ? "" : "(invalid) ") + "accountName:" + data.accountName + ",";
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
        return MyStringBuilder.formatKeyValue(this, members);
    }
    
    public JSONObject toJson() {
        return data.updateFrom(this).toJSon();
    }

    public String toAccountButtonText() {
        String accountButtonText = getShortestUniqueAccountName();
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
        if (!data.accountName.equals(myAccount.data.accountName)) return false;
        return StringUtil.equalsNotEmpty(actor.oid, myAccount.actor.oid);
    }

    @Override
    public int hashCode() {
        int result = data.accountName.hashCode();
        if (!StringUtil.isEmpty(actor.oid)) {
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

    public long getLastSyncSucceededDate() {
        return (isValid() && isPersistent())
                ? data.myContext().timelines()
                    .filter(false, TriState.UNKNOWN, TimelineType.UNKNOWN, actor, Origin.EMPTY)
                    .map(Timeline::getSyncSucceededDate).max(Long::compareTo).orElse(0L)
                : 0L;
    }

    public boolean hasAnyTimelines() {
        for (Timeline timeline : data.myContext().timelines().values()) {
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

        private volatile MyAccount myAccount;

        /**
         * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
         */
        public static Builder fromAccountName(AccountName accountName) {
            return fromMyAccount(myAccountFromName(accountName));
        }

        /**
         * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
         */
        private static MyAccount myAccountFromName(AccountName accountName) {
            MyAccount persistentAccount = accountName.myContext().accounts().fromAccountName(accountName);
            return persistentAccount.isValid()
                    ? persistentAccount
                    : new MyAccount(accountName);
        }

        /** Loads existing account from Persistence */
        static Builder loadFromAndroidAccount(MyContext myContext, @NonNull android.accounts.Account account) {
            return loadFromAccountData(AccountData.fromAndroidAccount(myContext, account),"fromAndroidAccount");
        }

        static Builder loadFromAccountData(@NonNull AccountData accountData, String method) {
            MyAccount myAccount = new MyAccount(accountData);
            Builder builder = fromMyAccount(myAccount);
            if (!MyContextHolder.INSTANCE.isOnRestore()) builder.fixInconsistenciesWithChangedEnvironmentSilently();
            builder.logLoadResult(method);
            return builder;
        }

        static Builder fromMyAccount(MyAccount ma) {
            return new Builder(ma);
        }

        private Builder(MyAccount myAccount) {
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
                MyLog.i(TAG, method + " Failed to load: Invalid account; " + this + "\n" +
                        MyLog.getStackTrace(new Exception()));
            }
        }


        public void setOrigin(Origin origin) {
            rebuildMyAccount(origin, getUniqueName());
        }

        public void setUniqueName(String uniqueName) {
            rebuildMyAccount(getOrigin(), uniqueName);
        }

        public Builder setOAuth(boolean isOauthBoolean) {
            TriState isOauth = isOauthBoolean == getOrigin().isOAuthDefault()
                    ? TriState.UNKNOWN
                    : TriState.fromBoolean(isOauthBoolean);
            myAccount.setOAuth(isOauth);
            return this;
        }

        void rebuildMyAccount(MyContext myContext) {
            rebuildMyAccount(myContext.origins().fromName(getOrigin().getName()), getUniqueName());
        }

        private void rebuildMyAccount(Origin origin, String uniqueName) {
            rebuildMyAccount(AccountName.fromOriginAndUniqueName(origin, uniqueName));
        }

        void rebuildMyAccount(AccountName accountName) {
            MyAccount ma = accountName.myContext().accounts().fromAccountName(accountName.getName());
            myAccount = ma.isValid()
                    ? ma
                    : new MyAccount(getAccount().data.withAccountName(accountName));
        }

        public Origin getOrigin() {
            return myAccount.getOrigin();
        }

        public String getUniqueName() {
            return getAccount().getOAccountName().getUniqueName();
        }

        public String getPassword() {
            return getAccount().getPassword();
        }

        boolean isOAuth() {
            return getAccount().isOAuth();
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
            myAccount.data.setDataBoolean(KEY_DELETED, true);
        }

        public void setSyncedAutomatically(boolean syncedAutomatically) {
            myAccount.isSyncedAutomatically = syncedAutomatically;
        }

        public void setOrder(int order) {
            this.myAccount.order = order;
        }

        void save() {
            if (saveSilently().getOrElse(false) && myContext().isReady()) {
                MyPreferences.onPreferencesChanged();
            }
        }

        /** Save this MyAccount to AccountManager */
        Try<Boolean> saveSilently() {
            if (myAccount.isValid()) {
                return myAccount.getNewOrExistingAndroidAccount()
                        .onSuccess(account -> myAccount.data.updateFrom(myAccount))
                        .flatMap(account -> myAccount.data.saveIfChanged(account))
                        .onFailure(e -> myAccount.data.setPersistent(false))
                        .onSuccess(result1 -> {
                            MyLog.v(this, () -> (result1 ? " Saved " : " Didn't change ") +
                                    this.toString());
                            myContext().accounts().addIfAbsent(myAccount);
                            if (myContext().isReady() && !myAccount.hasAnyTimelines()) {
                                new TimelineSaver(myContext()).setAddDefaults(true).setAccount(myAccount).executeNotOnUiThread();
                            }
                        })
                        .onFailure(e -> MyLog.v(this, () -> "Failed to save" + this.toString() +
                                "; Error: " + e.getMessage()));
            } else {
                MyLog.v(TAG, () -> "Didn't save invalid account: " + myAccount);
                return Try.failure(new Exception());
            }
        }

        Try<Builder> getOriginConfig() {
            return getConnection().getConfig().map(config -> {
                if (config.nonEmpty()) {
                    Origin.Builder originBuilder = new Origin.Builder(myAccount.getOrigin());
                    originBuilder.save(config);
                    MyLog.v(this, "Get Origin config succeeded " + config);
                }
                return this;
            });
        }

        public Try<Builder> onCredentialsVerified(@NonNull Actor actor) {
            boolean ok = actor.nonEmpty() && StringUtil.nonEmpty(actor.oid) && actor.isUsernameValid();
            boolean errorSettingUsername = !ok;

            boolean credentialsOfOtherAccount = false;
            // We are comparing usernames ignoring case, but we fix correct case
            // as the Originating system tells us.
            if (ok && !StringUtil.isEmpty(myAccount.getUsername())
                    && myAccount.data.accountName.username.compareToIgnoreCase(actor.getUsername()) != 0) {
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
                    myAccount.actor.actorId = myAccount.data.getDataLong(KEY_ACTOR_ID, myAccount.actor.actorId);
                } else {
                    new DataUpdater(myAccount).onActivity(actor.update(actor));
                }
                if (!isPersistent()) {
                    // Now we know the name (or proper case of the name) of this Account!
                    boolean sameName = myAccount.data.accountName.getUniqueName().equals(actor.getUniqueName());
                    if (!sameName) {
                        MyLog.i(this, "name changed from " + myAccount.data.accountName.getUniqueName() +
                                " to " + actor.getUniqueName());
                        myAccount.data.updateFrom(myAccount);
                        AccountData newData = myAccount.data.withAccountName(
                                AccountName.fromOriginAndUniqueName(myAccount.getOrigin(), actor.getUniqueName()));
                        myAccount = loadFromAccountData(newData, "onCredentialsVerified").myAccount;
                    }
                    save();
                }
            }
            if (!ok || !myAccount.getCredentialsPresent()) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED);
            }
            save();

            if (credentialsOfOtherAccount) {
                MyLog.e(TAG, myContext().context().getText(R.string.error_credentials_of_other_user) + ": " +
                        actor.getUniqueNameWithOrigin() +
                        " account name: " + myAccount.getAccountName() +
                        " vs username: " + actor.getUsername());
                return Try.failure(new ConnectionException(StatusCode.CREDENTIALS_OF_OTHER_ACCOUNT, actor.getUniqueNameWithOrigin()));
            }
            if (errorSettingUsername) {
                String msg = myContext().context().getText(R.string.error_set_username) + " " + actor.getUsername();
                MyLog.e(TAG, msg);
                return Try.failure(new ConnectionException(StatusCode.AUTHENTICATION_ERROR, msg));
            }
            return Try.success(this);
        }

        public void setUserTokenWithSecret(String token, String secret) {
            getConnection().setUserTokenWithSecret(token, secret);
        }

        public void setCredentialsVerificationStatus(CredentialsVerificationStatus cv) {
            myAccount.credentialsVerified = cv;
            if (cv != CredentialsVerificationStatus.SUCCEEDED) {
                getConnection().clearAuthInformation();
            }
        }

        public void registerClient() throws ConnectionException {
            MyLog.v(TAG, () -> "Registering client application for " + myAccount.getUsername());
            myAccount.setConnection();
            getConnection().registerClientForAccount();
        }

        Connection getConnection() {
            return myAccount.getConnection().isEmpty()
                ? Connection.fromOrigin(getOrigin(), TriState.fromBoolean(isOAuth()))
                : myAccount.getConnection();
        }

        public void setPassword(String password) {
            if (StringUtil.notEmpty(password, "").compareTo(getConnection().getPassword()) != 0) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER);
                getConnection().setPassword(password);
            }
        }

        private void assignActorId() {
            myAccount.actor.actorId = myAccount.getOrigin().usernameToId(myAccount.getUsername());
            if (myAccount.actor.actorId == 0) {
                try {
                    new DataUpdater(myAccount).onActivity(myAccount.actor.update(myAccount.actor));
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
            dest.writeParcelable(myAccount.data, flags);
        }

        public static final Creator<Builder> CREATOR = new Creator<Builder>() {
            @Override
            public Builder createFromParcel(Parcel source) {
                return loadFromAccountData(AccountData.CREATOR.createFromParcel(source), "createFromParcel");
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

        public MyContext myContext() {
            return myAccount.data.myContext();
        }
    }
}
