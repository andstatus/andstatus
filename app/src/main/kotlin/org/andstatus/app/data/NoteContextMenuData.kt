/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin

/**
 * Helper class to find out a relation of a Note to [.myAccount]
 * @author yvolk@yurivolkov.com
 */
class NoteContextMenuData(val noteForAnyAccount: NoteForAnyAccount, myAccount: MyAccount) {
    private var isAuthorMySucceededMyAccount = false
    private val myAccount: MyAccount
    var isSubscribed = false
    var isAuthor = false
    var isActor = false
    private var isRecipient = false
    var isConversationParticipant = false
    var favorited = false
    var reblogged = false
    var actorFollowed = false
    var authorFollowed = false

    private fun calculateMyAccount(origin: Origin, ma: MyAccount): MyAccount {
        return if (ma == null || !origin.isValid() || ma.origin != origin || ma.nonValid) {
            MyAccount.EMPTY
        } else ma
    }

    private fun loadData() {
        isRecipient = noteForAnyAccount.audience.findSame(myAccount.actor).isSuccess
        isAuthor = myAccount.actorId == noteForAnyAccount.author.actorId
        isAuthorMySucceededMyAccount = isAuthor && myAccount.isValidAndSucceeded()
        isConversationParticipant = noteForAnyAccount.conversationParticipants
            .any { conversationViewItem -> conversationViewItem.author.actor.isSame(myAccount.actor) }

        val actorToNote = MyQuery.favoritedAndReblogged(noteForAnyAccount.myContext,
            noteForAnyAccount.noteId, myAccount.actorId)
        favorited = actorToNote.favorited
        reblogged = actorToNote.reblogged
        isSubscribed = actorToNote.subscribed
        authorFollowed = myAccount.isFollowing(noteForAnyAccount.author)
        isActor = noteForAnyAccount.actor.actorId == myAccount.actorId
        actorFollowed = !isActor &&
            if (noteForAnyAccount.actor.actorId == noteForAnyAccount.author.actorId) authorFollowed
            else myAccount.isFollowing(noteForAnyAccount.actor)
    }

    fun getMyAccount(): MyAccount {
        return myAccount
    }

    fun getMyActor(): Actor {
        return myAccount.actor
    }

    fun isTiedToThisAccount(): Boolean {
        return (isRecipient || favorited || reblogged || isAuthor ||
            isConversationParticipant || actorFollowed || authorFollowed)
    }

    fun hasPrivateAccess(): Boolean {
        return isRecipient || isAuthor
    }

    fun isAuthorSucceededMyAccount(): Boolean {
        return isAuthorMySucceededMyAccount
    }

    override fun toString(): String {
        return TAG + "{" +
                "noteForAnyAccount=" + noteForAnyAccount +
                ", isAuthorMySucceededMyAccount=" + isAuthorMySucceededMyAccount +
                ", myAccount=" + myAccount.getAccountName() +
                ", accountActorId=" + myAccount.actorId +
                ", isSubscribed=" + isSubscribed +
                ", isAuthor=" + isAuthor +
                ", isActor=" + isActor +
                ", isRecipient=" + isRecipient +
                ", favorited=" + favorited +
                ", reblogged=" + reblogged +
                ", actorFollowed=" + actorFollowed +
                ", authorFollowed=" + authorFollowed +
                '}'
    }

    companion object {
        private val TAG: String = NoteContextMenuData::class.java.simpleName
        val EMPTY: NoteContextMenuData = NoteContextMenuData(NoteForAnyAccount.EMPTY, MyAccount.EMPTY)

        fun getBestAccountToDownloadNote(myContext: MyContext, noteId: Long): MyAccount {
            val noteForAnyAccount = NoteForAnyAccount(myContext, 0, noteId)
            var subscribedFound = false
            var bestFit = EMPTY
            for (menuData in getMenuData(myContext, noteForAnyAccount)) {
                if (menuData.hasPrivateAccess()) {
                    bestFit = menuData
                    break
                }
                if (menuData.isSubscribed) {
                    bestFit = menuData
                    subscribedFound = true
                }
                if (menuData.isTiedToThisAccount() && !subscribedFound) {
                    bestFit = menuData
                }
            }
            return if (bestFit == EMPTY) myContext.accounts.getFirstPreferablySucceededForOrigin(noteForAnyAccount.origin)
            else bestFit.myAccount
        }

        private fun getMenuData(myContext: MyContext, noteForAnyAccount: NoteForAnyAccount): MutableList<NoteContextMenuData> {
            return myContext.accounts.succeededForSameOrigin(noteForAnyAccount.origin)
                    .map { a: MyAccount -> NoteContextMenuData(noteForAnyAccount, a) }.toMutableList()
        }

        fun getAccountToActOnNote(myContext: MyContext, activityId: Long, noteId: Long,
                                  selectedActingAccount: MyAccount,
                                  currentAccount: MyAccount): NoteContextMenuData {
            val noteForAnyAccount = NoteForAnyAccount(myContext, activityId, noteId)
            val menuDataList = getMenuData(myContext, noteForAnyAccount)
            val acting = menuDataList.firstOrNull { menuData: NoteContextMenuData -> menuData.myAccount == selectedActingAccount }
                ?: EMPTY
            if (acting != EMPTY) return acting
            var bestFit =
                menuDataList.firstOrNull { menuData: NoteContextMenuData -> menuData.myAccount == currentAccount }
                    ?: EMPTY
            for (menuData in menuDataList) {
                if (menuData.isConversationParticipant) {
                    bestFit = menuData
                    break
                }
                if (menuData.hasPrivateAccess()) {
                    bestFit = menuData
                    break
                }
                if (menuData.isSubscribed && !bestFit.isSubscribed) {
                    bestFit = menuData
                }
                if (menuData.isTiedToThisAccount() && !bestFit.isTiedToThisAccount()) {
                    bestFit = menuData
                }
                if (!bestFit.myAccount.isValidAndSucceeded()) {
                    bestFit = menuData
                }
            }
            return bestFit
        }
    }

    init {
        this.myAccount = calculateMyAccount(noteForAnyAccount.origin, myAccount)
        if (this.myAccount.isValid) {
            loadData()
        }
    }
}
