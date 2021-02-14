package org.andstatus.app.account;

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount.Builder;
import org.andstatus.app.backup.MyBackupAgent;
import org.andstatus.app.backup.MyBackupDataInput;
import org.andstatus.app.backup.MyBackupDataOutput;
import org.andstatus.app.backup.MyBackupDescriptor;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.SqlIds;
import org.andstatus.app.data.converter.AccountConverter;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StopWatch;
import org.andstatus.app.util.StringUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;

public class MyAccounts implements IsEmpty {
    /** Current account is the first in this list */
    public final List<MyAccount> recentAccounts = new CopyOnWriteArrayList<>();

    private final MyContext myContext;
    private final SortedSet<MyAccount> myAccounts = new ConcurrentSkipListSet<>();
    private int distinctOriginsCount = 0;

    private MyAccounts(MyContext myContext) {
        this.myContext = myContext;
    }

    @NonNull
    public Set<MyAccount> get() {
        return myAccounts;
    }
    
    public boolean isEmpty() {
        return myAccounts.isEmpty();
    }
    
    public int size() {
        return myAccounts.size();
    }
    
    public MyAccounts initialize() {
        StopWatch stopWatch = StopWatch.createStarted();
        myAccounts.clear();
        recentAccounts.clear();
        for (android.accounts.Account account : AccountUtils.getCurrentAccounts(myContext.context())) {
            MyAccount ma = Builder.loadFromAndroidAccount(myContext, account).getAccount();
            if (ma.isValid()) {
                myAccounts.add(ma);
            } else {
                MyLog.w(this, "The account is invalid: " + ma);
            }
        }
        calculateDistinctOriginsCount();
        recentAccounts.addAll(myAccounts.stream().limit(3).collect(Collectors.toList()));
        MyLog.i(this, "accountsInitializedMs:" + stopWatch.getTime() + "; "
            + this.myAccounts.size() + " accounts in " + distinctOriginsCount + " origins");
        return this;
    }

    public MyAccount getDefaultAccount() {
        return myAccounts.isEmpty() ? MyAccount.EMPTY : myAccounts.iterator().next();
    }

    public int getDistinctOriginsCount() {
        return distinctOriginsCount;
    }
    
    private void calculateDistinctOriginsCount() {
        Set<Origin> origins = new HashSet<>();
        for (MyAccount ma : myAccounts) {
            origins.add(ma.getOrigin());
        }
        distinctOriginsCount = origins.size();
    }
    
    public static MyAccounts newEmpty(MyContext myContext) {
        return new MyAccounts(myContext);
    }
    
    /**
     * @return Was the MyAccount (and Account) deleted?
     */
    public boolean delete(MyAccount ma) {
        MyAccount myAccountToDelete = myAccounts.stream().filter(myAccount -> myAccount.equals(ma))
                .findFirst().orElse(MyAccount.EMPTY);
        if (myAccountToDelete.nonValid()) return false;

        MyAccount.Builder.fromMyAccount(myAccountToDelete).deleteData();
        myAccounts.remove(myAccountToDelete);
        MyPreferences.onPreferencesChanged();
        return true;
    }

    /**
     * Find persistent MyAccount by accountName in local cache AND
     * in Android AccountManager
     * 
     * @return Invalid account if was not found
     */
    @NonNull
    public MyAccount fromAccountName(String accountNameString) {
        return  fromAccountName(AccountName.fromAccountName(myContext, accountNameString));
    }

    /**
     * Find persistent MyAccount by accountName in local cache AND
     * in Android AccountManager
     *
     * @return Invalid account if was not found
     */
    @NonNull
    public MyAccount fromAccountName(AccountName accountName) {
        if (accountName == null || !accountName.isValid) return MyAccount.EMPTY;

        for (MyAccount persistentAccount : myAccounts) {
            if (persistentAccount.getAccountName().equals(accountName.getName())) {
                return persistentAccount;
            }
        }
        for (android.accounts.Account androidAccount : AccountUtils.getCurrentAccounts(myContext.context())) {
            if (accountName.toString().equals(androidAccount.name)) {
                MyAccount myAccount = Builder.loadFromAndroidAccount(myContext, androidAccount).getAccount();
                if (myAccount.isValid()) {
                    myAccounts.add(myAccount);
                }
                MyPreferences.onPreferencesChanged();
                return myAccount;
            }
        }
        return MyAccount.EMPTY;
    }

    /**
     * Get MyAccount by the ActorId. The MyAccount found may be from another origin
     * Please note that a valid Actor may not have an Account (in AndStatus)
     * @return EMPTY account if was not found
     */
    @NonNull
    public MyAccount fromActorId(long actorId) {
        if (actorId == 0) return MyAccount.EMPTY;
        return fromActor(Actor.load(myContext, actorId), false, false);
    }

    @NonNull
    public MyAccount fromActorOfSameOrigin(@NonNull Actor actor) {
        return fromActor(actor, true, false);
    }

    /** Doesn't take origin into account */
    @NonNull
    public MyAccount fromActorOfAnyOrigin(@NonNull Actor actor) {
        return fromActor(actor, false, false);
    }

    @NonNull
    private MyAccount fromActor(@NonNull Actor other, boolean sameOriginOnly, boolean succeededOnly) {
        return myAccounts.stream().filter(ma -> ma.isValidAndSucceeded() || !succeededOnly)
                .filter(ma -> ma.getActor().isSame(other, sameOriginOnly))
                .findFirst().orElseGet(() -> fromMyActors(other, sameOriginOnly));
    }

    @NonNull
    private MyAccount fromMyActors(@NonNull Actor other, boolean sameOriginOnly) {
        return myAccounts.stream().filter(ma ->
                (!sameOriginOnly || ma.getOrigin().equals(other.origin))
                && myContext.users().myActors.values().stream()
                        .filter(actor -> actor.user.userId == ma.getActor().user.userId)
                        .anyMatch(actor -> actor.isSame(other, sameOriginOnly)))
                .findFirst().orElse(MyAccount.EMPTY);
    }

    /** My account, which can be used to sync the "other" actor's data and to interact with that actor */
    @NonNull
    public MyAccount toSyncThatActor(@NonNull Actor other) {
        return other.isEmpty() ? MyAccount.EMPTY
                : Stream.of(fromActor(other, true, true))
                .filter(MyAccount::isValid).findFirst()
                .orElseGet(() -> forRelatedActor(other, true, true)
                                .orElseGet(() -> other.origin.isEmpty()
                                        ? MyAccount.EMPTY
                                        : getFirstPreferablySucceededForOrigin(other.origin))
                );
    }

    private Optional<MyAccount> forRelatedActor(Actor relatedActor, boolean sameOriginOnly, boolean succeededOnly) {
        Optional<MyAccount> forFriend = forFriendOfFollower(relatedActor, sameOriginOnly, succeededOnly,
                myContext.users().friendsOfMyActors);
        if (forFriend.isPresent()) return forFriend;

        return forFriendOfFollower(relatedActor, sameOriginOnly, succeededOnly, myContext.users().followersOfMyActors);
    }

    private Optional<MyAccount> forFriendOfFollower(Actor friend, boolean sameOriginOnly, boolean succeededOnly,
                                                    Map<Long, Set<Long>> friendsOrFollowers) {
        return friendsOrFollowers.getOrDefault(friend.actorId, Collections.emptySet()).stream()
                .map(this::fromActorId)
                .filter(ma -> ma.isValidAndSucceeded() || !succeededOnly)
                .filter(ma -> !sameOriginOnly || ma.getOrigin().equals(friend.origin))
                .sorted()
                .findFirst();
    }

    /** Doesn't take origin into account */
    @NonNull
    public MyAccount fromWebFingerId(String webFingerId) {
        if (StringUtil.isEmpty(webFingerId)) return MyAccount.EMPTY;
        return myAccounts.stream().filter(myAccount -> myAccount.getWebFingerId().equals(webFingerId))
                .findFirst()
                .orElse(MyAccount.EMPTY);
    }

    /**
     * @return current MyAccount (MyAccount selected by the User) or EMPTY if no persistent accounts exist
     */
    @NonNull
    public MyAccount getCurrentAccount() {
        return recentAccounts.stream().findFirst().orElse(myAccounts.stream().findFirst().orElse(MyAccount.EMPTY));
    }

    /**
     * @return 0 if no valid persistent accounts exist
     */
    public long getCurrentAccountActorId() {
        return getCurrentAccount().getActorId();
    }

    @NonNull
    public MyAccount getFirstSucceeded() {
        return getFirstPreferablySucceededForOrigin(Origin.EMPTY);
    }

    /**
     * Return first verified and autoSynced MyAccount of the provided origin.
     * If not auto synced, at least verified and succeeded,
     * If there is no verified account, any account of this Origin is been returned.
     * Otherwise invalid account is returned;
     * @param origin May be EMPTY to search in any Origin
     * @return EMPTY account if not found
     */
    @NonNull
    public MyAccount getFirstPreferablySucceededForOrigin(@NonNull Origin origin) {
        return getFirstSucceededForOriginsStrict(Collections.singletonList(origin));
    }

    /**
     * Return first verified and autoSynced MyAccount of the provided origins.
     * If not auto synced, at least verified and succeeded,
     * If there is no verified account, any account of this Origin is been returned.
     * Otherwise invalid account is returned;
     * @param origins May contain Origin.EMPTY to search in any Origin
     * @return EMPTY account if not found
     */
    @NonNull
    private MyAccount getFirstSucceededForOriginsStrict(@NonNull Collection<Origin> origins) {
        MyAccount ma = MyAccount.EMPTY;
        MyAccount maSucceeded = MyAccount.EMPTY;
        MyAccount maSynced = MyAccount.EMPTY;
        for (MyAccount myAccount : myAccounts) {
            for (Origin origin : origins) {
                if (!origin.isValid() || myAccount.getOrigin().equals(origin)) {
                    if (ma.nonValid()) {
                        ma = myAccount;
                    }
                    if (myAccount.isValidAndSucceeded()) {
                        if (!ma.isValidAndSucceeded()) {
                            maSucceeded = myAccount;
                        }
                        if (myAccount.isSyncedAutomatically()) {
                            maSynced = myAccount;
                            break;
                        }
                    }
                }
                if (maSynced.isValid()) return maSynced;
            }
        }
        return maSynced.isValid()
                ? maSynced
                : (maSucceeded.isValid()
                ? maSucceeded
                : ma);
    }

    /** @return this account if there are no others */
    @NonNull
    public MyAccount firstOtherSucceededForSameOrigin(Origin origin, MyAccount thisAccount) {
        return succeededForSameOrigin(origin).stream().filter(ma -> !ma.equals(thisAccount))
                .sorted().findFirst().orElse(thisAccount);
    }

    /**
     * Return verified and autoSynced MyAccounts for the Origin
     * @param origin May be empty to search in any Origin
     * @return Empty Set if not found
     */
    @NonNull
    public Set<MyAccount> succeededForSameOrigin(Origin origin) {
        return origin.isEmpty()
                ? myAccounts.stream().filter(MyAccount::isValidAndSucceeded).collect(Collectors.toSet())
                : myAccounts.stream()
                .filter(ma -> origin.equals(ma.getOrigin()))
                .filter(MyAccount::isValidAndSucceeded)
                .collect(Collectors.toSet());
    }

    /** @return this account if there are no others */
    @NonNull
    public MyAccount firstOtherSucceededForSameUser(Actor actor, MyAccount thisAccount) {
        return succeededForSameUser(actor).stream().filter(ma -> !ma.equals(thisAccount))
                .sorted().findFirst().orElse(thisAccount);
    }

    /**
     * Return verified and autoSynced MyAccounts for Origin-s, where User of this Actor is known.
     * @param actor May be empty to search in any Origin
     * @return Empty Set if not found
     */
    @NonNull
    public Set<MyAccount> succeededForSameUser(Actor actor) {
        List<Origin> origins = actor.user.knownInOrigins(myContext);
        return origins.isEmpty()
                ? myAccounts.stream().filter(MyAccount::isValidAndSucceeded).collect(Collectors.toSet())
                : myAccounts.stream()
                    .filter(ma -> origins.contains(ma.getOrigin()))
                    .filter(MyAccount::isValidAndSucceeded)
                    .collect(Collectors.toSet());
    }

    public boolean hasSyncedAutomatically() {
        for (MyAccount ma : myAccounts) {
            if (ma.shouldBeSyncedAutomatically()) return true;
        }
        return false;
    }

    /** @return 0 if no syncing is needed */
    public long minSyncIntervalMillis() {
        return myAccounts.stream()
                .filter(MyAccount::shouldBeSyncedAutomatically)
                .map(MyAccount::getEffectiveSyncFrequencyMillis)
                .min(Long::compareTo).orElse(0L);
    }

    /** Should not be called from UI thread
     * Find MyAccount, which may be linked to a note in this origin.
     * First try two supplied accounts, then try any other existing account
     */
    @NonNull
    public MyAccount getAccountForThisNote(Origin origin, MyAccount firstAccount, MyAccount preferredAccount,
                                           boolean succeededOnly)  {
        final String method = "getAccountForThisNote";
        MyAccount ma = firstAccount == null ? MyAccount.EMPTY : firstAccount;
        if (!accountFits(ma, origin, succeededOnly)) {
            ma = betterFit(ma, preferredAccount == null ? MyAccount.EMPTY : preferredAccount, origin, succeededOnly);
        }
        if (!accountFits(ma, origin, succeededOnly)) {
            ma = betterFit(ma, getFirstPreferablySucceededForOrigin(origin), origin, succeededOnly);
        }
        if (!accountFits(ma, origin, false)) {
            ma = MyAccount.EMPTY;
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; origin=" + origin.getName()
                    + "; account1=" + ma
                    + (ma.equals(preferredAccount) ? "" : "; account2=" + preferredAccount)
                    + (succeededOnly ? "; succeeded only" : ""));
        }
        return ma;
    }

    private boolean accountFits(MyAccount ma, @NonNull Origin origin, boolean succeededOnly) {
        return ma != null
                && (succeededOnly ? ma.isValidAndSucceeded() : ma.isValid())
                && (!origin.isValid() || ma.getOrigin().equals(origin));
    }

    @NonNull
    private MyAccount betterFit(@NonNull MyAccount oldMa, @NonNull MyAccount newMa, @NonNull Origin origin,
                                boolean succeededOnly) {
        if (accountFits(oldMa, origin, succeededOnly) || !accountFits(newMa, origin, false)) {
            return oldMa;
        }
        if (oldMa.nonValid() && newMa.isValid()) {
            return newMa;
        }
        return oldMa;
    }
    
    /** Set provided MyAccount as the Current one */
    public void setCurrentAccount(MyAccount ma) {
        MyAccount prevAccount = getCurrentAccount();
        if (ma == null || ma.nonValid() || ma.equals(prevAccount)) return;

        MyLog.v(this, () -> "Changing current account from '" + prevAccount.getAccountName()
                + "' to '" + ma.getAccountName() + "'");
        recentAccounts.remove(ma);
        recentAccounts.add(0, ma);
    }

    public void onDefaultSyncFrequencyChanged() {
        long syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
        for (MyAccount ma : myAccounts) {
            if (ma.getSyncFrequencySeconds() <= 0) AccountUtils.getExistingAndroidAccount(ma.getOAccountName()
            ).onSuccess(account ->
                        AccountUtils.setSyncFrequencySeconds(account, syncFrequencySeconds));
        }
    }

    public List<MyAccount> accountsToSync() {
        boolean syncedAutomaticallyOnly = hasSyncedAutomatically();
        return myAccounts.stream().filter( myAccount -> accountToSyncFilter(myAccount, syncedAutomaticallyOnly))
                .collect(toList());
    }

    private boolean accountToSyncFilter(MyAccount account, boolean syncedAutomaticallyOnly) {
        if ( !account.isValidAndSucceeded()) {
            MyLog.v(this, () -> "Account '" + account.getAccountName() + "'" +
                    " skipped as invalid authenticated account");
            return false;
        }
        if (syncedAutomaticallyOnly && !account.isSyncedAutomatically()) {
            MyLog.v(this, () -> "Account '" + account.getAccountName() + "'" +
                    " skipped as it is not synced automatically");
            return false;
        }
        return true;
    }

    public long onBackup(MyBackupDataOutput data, MyBackupDescriptor newDescriptor) throws IOException {
        try {
            JSONArray jsa = new JSONArray();
            myAccounts.forEach(ma -> jsa.put(ma.toJson()));
            byte[] bytes = jsa.toString(2).getBytes(StandardCharsets.UTF_8);
            data.writeEntityHeader(MyBackupAgent.KEY_ACCOUNT, bytes.length, ".json");
            data.writeEntityData(bytes, bytes.length);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        newDescriptor.setAccountsCount(myAccounts.size());
        return myAccounts.size();
    }

    /** Returns count of restores objects */
    public long onRestore(MyBackupDataInput data, MyBackupDescriptor newDescriptor) throws IOException {
        AtomicLong restoredCount = new AtomicLong();
        final String method = "onRestore";
        MyLog.i(this, method + "; started, " + I18n.formatBytes(data.getDataSize()));
        byte[] bytes = new byte[data.getDataSize()];
        int bytesRead = data.readEntityData(bytes, 0, bytes.length);
        try {
            JSONArray jsa = new JSONArray(new String(bytes, 0, bytesRead, StandardCharsets.UTF_8));
            for (int ind = 0; ind < jsa.length(); ind++) {
                int order = ind + 1;
                MyLog.v(this, method + "; restoring " + order + " of " + jsa.length());
                AccountConverter.convertJson(data.getMyContext(), (JSONObject) jsa.get(ind), false)
                        .onSuccess(jso -> {
                    AccountData accountData = AccountData.fromJson(myContext, jso, false);
                    MyAccount.Builder builder = Builder.loadFromAccountData(accountData, "fromJson");
                    CredentialsVerificationStatus verified = builder.getAccount().getCredentialsVerified();
                    if (verified != CredentialsVerificationStatus.SUCCEEDED) {
                        newDescriptor.getLogger().logProgress("Account " + builder.getAccount().getAccountName() +
                                " was not successfully verified");
                        builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED);
                    }
                    builder.saveSilently().onSuccess( r -> {
                        MyLog.v(this, method + "; restored " + order + ": " + builder.toString());
                        restoredCount.incrementAndGet();
                        if (verified != CredentialsVerificationStatus.SUCCEEDED) {
                            builder.setCredentialsVerificationStatus(verified);
                            builder.saveSilently();
                        }
                    }).onFailure( e -> {
                        MyLog.e(this, method + "; failed to restore " + order + ": " + builder.toString());
                    });
                });
            }
            if (restoredCount.get() != newDescriptor.getAccountsCount()) {
                throw new FileNotFoundException("Restored only " + restoredCount + " accounts of " +
                        newDescriptor.getAccountsCount());
            }
            newDescriptor.getLogger().logProgress("Restored " + restoredCount + " accounts");
        } catch (JSONException e) {
            throw new IOException(method, e);
        }
        return restoredCount.get();
    }

    @Override
    public String toString() {
        return "PersistentAccounts{" + myAccounts + '}';
    }

    @Override
    public int hashCode() {
        return myAccounts.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MyAccounts other = (MyAccounts) o;
        return myAccounts.equals(other.myAccounts);
    }

    public void reorderAccounts(List<MyAccount> reorderedItems) {
        int order = 0;
        boolean changed = false;
        for (MyAccount myAccount : reorderedItems) {
            order++;
            if (myAccount.getOrder() != order) {
                changed = true;
                MyAccount.Builder builder = Builder.fromMyAccount(myAccount);
                builder.setOrder(order);
                builder.save();
            }
        }
        if (changed) {
            MyPreferences.onPreferencesChanged();
        }
    }

    @NonNull
    public static SqlIds myAccountIds() {
        return SqlIds.fromIds(
            AccountUtils.getCurrentAccounts(myContextHolder.getNow().context()).stream()
            .map(account -> AccountData.fromAndroidAccount(myContextHolder.getNow(), account)
                    .getDataLong(MyAccount.KEY_ACTOR_ID, 0))
            .filter(id -> id > 0)
            .collect(toList())
        );
    }

    void addIfAbsent(@NonNull MyAccount myAccount) {
        if (!myAccounts.contains(myAccount)) myAccounts.add(myAccount);
        myContext.users().updateCache(myAccount.getActor());
    }
}
