package org.andstatus.app.account

import android.accounts.Account
import org.andstatus.app.backup.MyBackupAgent
import org.andstatus.app.backup.MyBackupDataInput
import org.andstatus.app.backup.MyBackupDataOutput
import org.andstatus.app.backup.MyBackupDescriptor
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.SqlIds
import org.andstatus.app.data.converter.AccountConverter
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.I18n
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StopWatch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

class MyAccounts private constructor(private val myContext: MyContext) : IsEmpty {
    /** Current account is the first in this list  */
    val recentAccounts: MutableList<MyAccount> = CopyOnWriteArrayList()
    private val myAccounts: SortedSet<MyAccount> = ConcurrentSkipListSet()
    private var distinctOriginsCount = 0
    fun get(): MutableSet<MyAccount> {
        return myAccounts
    }

    override val isEmpty: Boolean get() = myAccounts.isEmpty()

    fun size(): Int {
        return myAccounts.size
    }

    fun initialize(): MyAccounts {
        val stopWatch: StopWatch = StopWatch.createStarted()
        myAccounts.clear()
        recentAccounts.clear()
        for (account in AccountUtils.getCurrentAccounts(myContext.context())) {
            val ma: MyAccount = MyAccount.Builder.loadFromAndroidAccount(myContext, account).getAccount()
            if (ma.isValid) {
                myAccounts.add(ma)
            } else {
                MyLog.w(this, "The account is invalid: $ma")
            }
        }
        calculateDistinctOriginsCount()
        recentAccounts.addAll(myAccounts.stream().limit(3).collect(Collectors.toList()))
        MyLog.i(this, "accountsInitializedMs:" + stopWatch.time + "; "
                + myAccounts.size + " accounts in " + distinctOriginsCount + " origins")
        return this
    }

    fun getDefaultAccount(): MyAccount {
        return if (myAccounts.isEmpty()) MyAccount.EMPTY else myAccounts.iterator().next()
    }

    fun getDistinctOriginsCount(): Int {
        return distinctOriginsCount
    }

    private fun calculateDistinctOriginsCount() {
        val origins: MutableSet<Origin?> = HashSet()
        for (ma in myAccounts) {
            origins.add(ma.origin)
        }
        distinctOriginsCount = origins.size
    }

    /**
     * @return Was the MyAccount (and Account) deleted?
     */
    fun delete(ma: MyAccount?): Boolean {
        val myAccountToDelete = myAccounts.stream().filter { myAccount: MyAccount? -> myAccount == ma }
                .findFirst().orElse(MyAccount.EMPTY)
        if (myAccountToDelete.nonValid) return false
        MyAccount.Builder.fromMyAccount(myAccountToDelete).deleteData()
        myAccounts.remove(myAccountToDelete)
        MyPreferences.onPreferencesChanged()
        return true
    }

    /**
     * Find persistent MyAccount by accountName in local cache AND
     * in Android AccountManager
     *
     * @return Invalid account if was not found
     */
    fun fromAccountName(accountNameString: String?): MyAccount {
        return fromAccountName(AccountName.fromAccountName(myContext, accountNameString))
    }

    /**
     * Find persistent MyAccount by accountName in local cache AND
     * in Android AccountManager
     *
     * @return Invalid account if was not found
     */
    fun fromAccountName(accountName: AccountName?): MyAccount {
        if (accountName == null || !accountName.isValid) return MyAccount.EMPTY
        for (persistentAccount in myAccounts) {
            if (persistentAccount.getAccountName() == accountName.name) {
                return persistentAccount
            }
        }
        for (androidAccount in AccountUtils.getCurrentAccounts(myContext.context())) {
            if (accountName.toString() == androidAccount.name) {
                val myAccount: MyAccount = MyAccount.Builder.loadFromAndroidAccount(myContext, androidAccount).getAccount()
                if (myAccount.isValid) {
                    myAccounts.add(myAccount)
                }
                MyPreferences.onPreferencesChanged()
                return myAccount
            }
        }
        return MyAccount.EMPTY
    }

    /**
     * Get MyAccount by the ActorId. The MyAccount found may be from another origin
     * Please note that a valid Actor may not have an Account (in AndStatus)
     * @return EMPTY account if was not found
     */
    fun fromActorId(actorId: Long): MyAccount {
        return if (actorId == 0L) MyAccount.EMPTY else fromActor(Actor.load(myContext, actorId), false, false)
    }

    fun fromActorOfSameOrigin(actor: Actor): MyAccount {
        return fromActor(actor, true, false)
    }

    /** Doesn't take origin into account  */
    fun fromActorOfAnyOrigin(actor: Actor): MyAccount {
        return fromActor(actor, false, false)
    }

    private fun fromActor(other: Actor, sameOriginOnly: Boolean, succeededOnly: Boolean): MyAccount {
        return myAccounts.stream().filter { ma: MyAccount -> ma.isValidAndSucceeded() || !succeededOnly }
                .filter { ma: MyAccount -> ma.actor.isSame(other, sameOriginOnly) }
                .findFirst().orElseGet { fromMyActors(other, sameOriginOnly) }
    }

    private fun fromMyActors(other: Actor, sameOriginOnly: Boolean): MyAccount {
        return myAccounts.stream().filter { ma: MyAccount ->
            ((!sameOriginOnly || ma.origin == other.origin)
                    && myContext.users().myActors.values.stream()
                    .filter { actor: Actor -> actor.user.userId == ma.actor.user.userId }
                    .anyMatch { actor: Actor -> actor.isSame(other, sameOriginOnly) })
        }
                .findFirst().orElse(MyAccount.EMPTY)
    }

    /** My account, which can be used to sync the "other" actor's data and to interact with that actor  */
    fun toSyncThatActor(other: Actor): MyAccount {
        return if (other.isEmpty) MyAccount.EMPTY else Stream.of(fromActor(other, true, true))
                .filter { obj: MyAccount -> obj.isValid }.findFirst()
                .orElseGet {
                    forRelatedActor(other, true, true)
                            .orElseGet({ if (other.origin.isEmpty) MyAccount.EMPTY else getFirstPreferablySucceededForOrigin(other.origin) })
                }
    }

    private fun forRelatedActor(relatedActor: Actor, sameOriginOnly: Boolean, succeededOnly: Boolean): Optional<MyAccount> {
        val forFriend = forFriendOfFollower(relatedActor, sameOriginOnly, succeededOnly,
                myContext.users().friendsOfMyActors)
        return if (forFriend.isPresent()) forFriend else forFriendOfFollower(relatedActor, sameOriginOnly, succeededOnly, myContext.users().followersOfMyActors)
    }

    private fun forFriendOfFollower(friend: Actor, sameOriginOnly: Boolean, succeededOnly: Boolean,
                                    friendsOrFollowers: MutableMap<Long, MutableSet<Long>>): Optional<MyAccount> {
        return friendsOrFollowers.getOrDefault(friend.actorId, emptySet<Long>()).stream()
                .map { actorId: Long -> fromActorId(actorId) }
                .filter { ma: MyAccount -> ma.isValidAndSucceeded() || !succeededOnly }
                .filter { ma: MyAccount -> !sameOriginOnly || ma.origin == friend.origin }
                .sorted()
                .findFirst()
    }

    /** Doesn't take origin into account  */
    fun fromWebFingerId(webFingerId: String?): MyAccount {
        return if (webFingerId.isNullOrEmpty()) MyAccount.EMPTY else myAccounts.stream()
                .filter { myAccount: MyAccount -> myAccount.getWebFingerId() == webFingerId }
                .findFirst()
                .orElse(MyAccount.EMPTY)
    }

    /**
     * @return current MyAccount (MyAccount selected by the User) or EMPTY if no persistent accounts exist
     */
    val currentAccount: MyAccount get() = recentAccounts.stream()
            .findFirst().orElse(myAccounts.stream().findFirst().orElse(MyAccount.EMPTY))

    /**
     * @return 0 if no valid persistent accounts exist
     */
    val currentAccountActorId: Long get() = currentAccount.actorId

    fun getFirstSucceeded(): MyAccount {
        return getFirstPreferablySucceededForOrigin( Origin.EMPTY)
    }

    /**
     * Return first verified and autoSynced MyAccount of the provided origin.
     * If not auto synced, at least verified and succeeded,
     * If there is no verified account, any account of this Origin is been returned.
     * Otherwise invalid account is returned;
     * @param origin May be EMPTY to search in any Origin
     * @return EMPTY account if not found
     */
    fun getFirstPreferablySucceededForOrigin(origin: Origin): MyAccount {
        return getFirstSucceededForOriginsStrict(mutableListOf(origin))
    }

    /**
     * Return first verified and autoSynced MyAccount of the provided origins.
     * If not auto synced, at least verified and succeeded,
     * If there is no verified account, any account of this Origin is been returned.
     * Otherwise invalid account is returned;
     * @param origins May contain Origin.EMPTY to search in any Origin
     * @return EMPTY account if not found
     */
    private fun getFirstSucceededForOriginsStrict(origins: MutableCollection<Origin>): MyAccount {
        var ma: MyAccount = MyAccount.EMPTY
        var maSucceeded: MyAccount = MyAccount.EMPTY
        var maSynced: MyAccount = MyAccount.EMPTY
        for (myAccount in myAccounts) {
            for (origin in origins) {
                if (!origin.isValid() || myAccount.origin == origin) {
                    if (ma.nonValid) {
                        ma = myAccount
                    }
                    if (myAccount.isValidAndSucceeded()) {
                        if (!ma.isValidAndSucceeded()) {
                            maSucceeded = myAccount
                        }
                        if (myAccount.isSyncedAutomatically()) {
                            maSynced = myAccount
                            break
                        }
                    }
                }
                if (maSynced.isValid) return maSynced
            }
        }
        return if (maSynced.isValid) maSynced else if (maSucceeded.isValid) maSucceeded else ma
    }

    /** @return this account if there are no others
     */
    fun firstOtherSucceededForSameOrigin(origin: Origin, thisAccount: MyAccount): MyAccount {
        return succeededForSameOrigin(origin).stream()
                .filter { ma: MyAccount -> ma != thisAccount }
                .sorted().findFirst().orElse(thisAccount)
    }

    /**
     * Return verified and autoSynced MyAccounts for the Origin
     * @param origin May be empty to search in any Origin
     * @return Empty Set if not found
     */
    fun succeededForSameOrigin(origin: Origin): MutableSet<MyAccount> {
        return if (origin.isEmpty) myAccounts.stream()
                .filter { obj: MyAccount -> obj.isValidAndSucceeded() }.collect(Collectors.toSet()) else myAccounts.stream()
                .filter { ma: MyAccount -> origin == ma.origin }
                .filter { obj: MyAccount -> obj.isValidAndSucceeded() }
                .collect(Collectors.toSet())
    }

    /** @return this account if there are no others
     */
    fun firstOtherSucceededForSameUser(actor: Actor, thisAccount: MyAccount): MyAccount {
        return succeededForSameUser(actor).stream()
                .filter { ma: MyAccount -> ma != thisAccount }
                .sorted().findFirst().orElse(thisAccount)
    }

    /**
     * Return verified and autoSynced MyAccounts for Origin-s, where User of this Actor is known.
     * @param actor May be empty to search in any Origin
     * @return Empty Set if not found
     */
    fun succeededForSameUser(actor: Actor): MutableSet<MyAccount> {
        val origins = actor.user.knownInOrigins(myContext)
        return if (origins.isEmpty()) myAccounts.stream()
                .filter { obj: MyAccount -> obj.isValidAndSucceeded() }
                .collect(Collectors.toSet()) else myAccounts.stream()
                .filter { ma: MyAccount -> origins.contains(ma.origin) }
                .filter { obj: MyAccount -> obj.isValidAndSucceeded() }
                .collect(Collectors.toSet())
    }

    fun hasSyncedAutomatically(): Boolean {
        for (ma in myAccounts) {
            if (ma.shouldBeSyncedAutomatically()) return true
        }
        return false
    }

    /** @return 0 if no syncing is needed
     */
    fun minSyncIntervalMillis(): Long {
        return myAccounts.stream()
                .filter { obj: MyAccount -> obj.shouldBeSyncedAutomatically() }
                .map { obj: MyAccount -> obj.getEffectiveSyncFrequencyMillis() }
                .min { obj: Long, anotherLong: Long -> obj.compareTo(anotherLong) }.orElse(0L)
    }

    /** Should not be called from UI thread
     * Find MyAccount, which may be linked to a note in this origin.
     * First try two supplied accounts, then try any other existing account
     */
    fun getAccountForThisNote(origin: Origin, firstAccount: MyAccount?, preferredAccount: MyAccount?,
                              succeededOnly: Boolean): MyAccount {
        val method = "getAccountForThisNote"
        var ma = firstAccount ?: MyAccount.EMPTY
        if (!accountFits(ma, origin, succeededOnly)) {
            ma = betterFit(ma, preferredAccount ?: MyAccount.EMPTY, origin, succeededOnly)
        }
        if (!accountFits(ma, origin, succeededOnly)) {
            ma = betterFit(ma, getFirstPreferablySucceededForOrigin(origin), origin, succeededOnly)
        }
        if (!accountFits(ma, origin, false)) {
            ma = MyAccount.EMPTY
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; origin=" + origin.name
                    + "; account1=" + ma
                    + (if (ma == preferredAccount) "" else "; account2=$preferredAccount")
                    + if (succeededOnly) "; succeeded only" else "")
        }
        return ma
    }

    private fun accountFits(ma: MyAccount?, origin: Origin, succeededOnly: Boolean): Boolean {
        return (ma != null && (if (succeededOnly) ma.isValidAndSucceeded() else ma.isValid)
                && (!origin.isValid() || ma.origin == origin))
    }

    private fun betterFit(oldMa: MyAccount, newMa: MyAccount, origin: Origin,
                          succeededOnly: Boolean): MyAccount {
        if (accountFits(oldMa, origin, succeededOnly) || !accountFits(newMa, origin, false)) {
            return oldMa
        }
        return if (oldMa.nonValid && newMa.isValid) {
            newMa
        } else oldMa
    }

    /** Set provided MyAccount as the Current one  */
    fun setCurrentAccount(ma: MyAccount?) {
        val prevAccount = currentAccount
        if (ma == null || ma.nonValid || ma == prevAccount) return
        MyLog.v(this) {
            ("Changing current account from '" + prevAccount.getAccountName()
                    + "' to '" + ma.getAccountName() + "'")
        }
        recentAccounts.remove(ma)
        recentAccounts.add(0, ma)
    }

    fun onDefaultSyncFrequencyChanged() {
        val syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds()
        for (ma in myAccounts) {
            if (ma.getSyncFrequencySeconds() <= 0) AccountUtils.getExistingAndroidAccount(ma.getOAccountName()
            ).onSuccess { account: Account? -> AccountUtils.setSyncFrequencySeconds(account, syncFrequencySeconds) }
        }
    }

    fun accountsToSync(): MutableList<MyAccount?>? {
        val syncedAutomaticallyOnly = hasSyncedAutomatically()
        return myAccounts.stream().filter { myAccount: MyAccount -> accountToSyncFilter(myAccount, syncedAutomaticallyOnly) }
                .collect(Collectors.toList())
    }

    private fun accountToSyncFilter(account: MyAccount, syncedAutomaticallyOnly: Boolean): Boolean {
        if (!account.isValidAndSucceeded()) {
            MyLog.v(this) {
                "Account '" + account.getAccountName() + "'" +
                        " skipped as invalid authenticated account"
            }
            return false
        }
        if (syncedAutomaticallyOnly && !account.isSyncedAutomatically()) {
            MyLog.v(this) {
                "Account '" + account.getAccountName() + "'" +
                        " skipped as it is not synced automatically"
            }
            return false
        }
        return true
    }

    @Throws(IOException::class)
    fun onBackup(data: MyBackupDataOutput, newDescriptor: MyBackupDescriptor): Long {
        try {
            val jsa = JSONArray()
            myAccounts.forEach(Consumer { ma: MyAccount -> jsa.put(ma.toJson()) })
            val bytes = jsa.toString(2).toByteArray(StandardCharsets.UTF_8)
            data.writeEntityHeader(MyBackupAgent.KEY_ACCOUNT, bytes.size, ".json")
            data.writeEntityData(bytes, bytes.size)
        } catch (e: JSONException) {
            throw IOException(e)
        }
        newDescriptor.setAccountsCount(myAccounts.size.toLong())
        return myAccounts.size.toLong()
    }

    /** Returns count of restores objects  */
    @Throws(IOException::class)
    fun onRestore(data: MyBackupDataInput, newDescriptor: MyBackupDescriptor): Long {
        val restoredCount = AtomicLong()
        val method = "onRestore"
        MyLog.i(this, method + "; started, " + I18n.formatBytes(data.getDataSize().toLong()))
        val bytes = ByteArray(data.getDataSize())
        val bytesRead = data.readEntityData(bytes, 0, bytes.size)
        try {
            val jsa = JSONArray(String(bytes, 0, bytesRead, StandardCharsets.UTF_8))
            for (ind in 0 until jsa.length()) {
                val order = ind + 1
                MyLog.v(this, method + "; restoring " + order + " of " + jsa.length())
                AccountConverter.convertJson(data.getMyContext(), jsa[ind] as JSONObject, false)
                        .onSuccess { jso: JSONObject ->
                            val accountData: AccountData = AccountData.fromJson(myContext, jso, false)
                            val builder: MyAccount.Builder = MyAccount.Builder.loadFromAccountData(accountData, "fromJson")
                            val verified = builder.getAccount().getCredentialsVerified()
                            if (verified != CredentialsVerificationStatus.SUCCEEDED) {
                                newDescriptor.getLogger().logProgress("Account " + builder.getAccount().getAccountName() +
                                        " was not successfully verified")
                                builder.setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED)
                            }
                            builder.saveSilently().onSuccess { r: Boolean? ->
                                MyLog.v(this, "$method; restored $order: $builder")
                                restoredCount.incrementAndGet()
                                if (verified != CredentialsVerificationStatus.SUCCEEDED) {
                                    builder.setCredentialsVerificationStatus(verified)
                                    builder.saveSilently()
                                }
                            }.onFailure { e: Throwable? -> MyLog.e(this, "$method; failed to restore $order: $builder") }
                        }
            }
            if (restoredCount.get() != newDescriptor.getAccountsCount()) {
                throw FileNotFoundException("Restored only " + restoredCount + " accounts of " +
                        newDescriptor.getAccountsCount())
            }
            newDescriptor.getLogger().logProgress("Restored $restoredCount accounts")
        } catch (e: JSONException) {
            throw IOException(method, e)
        }
        return restoredCount.get()
    }

    override fun toString(): String {
        return "PersistentAccounts{$myAccounts}"
    }

    override fun hashCode(): Int {
        return myAccounts.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || other !is MyAccounts) {
            return false
        }
        return myAccounts == other.myAccounts
    }

    fun reorderAccounts(reorderedItems: MutableList<MyAccount>) {
        var order = 0
        var changed = false
        for (myAccount in reorderedItems) {
            order++
            if (myAccount.getOrder() != order) {
                changed = true
                val builder: MyAccount.Builder = MyAccount.Builder.fromMyAccount(myAccount)
                builder.setOrder(order)
                builder.save()
            }
        }
        if (changed) {
            MyPreferences.onPreferencesChanged()
        }
    }

    fun addIfAbsent(myAccount: MyAccount) {
        if (!myAccounts.contains(myAccount)) myAccounts.add(myAccount)
        myContext.users().updateCache(myAccount.actor)
    }

    companion object {
        fun newEmpty(myContext: MyContext): MyAccounts {
            return MyAccounts(myContext)
        }

        fun myAccountIds(): SqlIds {
            return SqlIds.fromIds(
                    AccountUtils.getCurrentAccounts( MyContextHolder.myContextHolder.getNow().context()).stream()
                            .map({ account: Account ->
                                AccountData.fromAndroidAccount( MyContextHolder.myContextHolder.getNow(), account)
                                        .getDataLong(MyAccount.KEY_ACTOR_ID, 0)
                            })
                            .filter { id: Long -> id > 0 }
                            .collect(Collectors.toList())
            )
        }
    }
}