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

package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;

import java.util.List;

import androidx.annotation.NonNull;

import static java.util.stream.Collectors.toList;

/**
 * Helper class to find out a relation of a Note to {@link #myAccount}
 * @author yvolk@yurivolkov.com
 */
public class AccountToNote {
    public static final AccountToNote EMPTY = new AccountToNote(NoteForAnyAccount.EMPTY, MyAccount.EMPTY);
    @NonNull
    public final NoteForAnyAccount noteForAnyAccount;
    private boolean isAuthorMySucceededMyAccount = false;
    @NonNull
    private final MyAccount myAccount;
    public boolean isSubscribed = false;
    public boolean isAuthor = false;
    public boolean isActor = false;
    private boolean isRecipient = false;
    public boolean favorited = false;
    public boolean reblogged = false;
    public boolean actorFollowed = false;
    public boolean authorFollowed = false;

    public static MyAccount getBestAccountToDownloadNote(MyContext myContext, long noteId) {
        NoteForAnyAccount noteForAnyAccount = new NoteForAnyAccount(myContext, 0, noteId);
        boolean subscribedFound = false;
        AccountToNote bestAccountToNote = AccountToNote.EMPTY;
        for(AccountToNote accountToNote : getAccountsForNote(myContext, noteForAnyAccount)) {
            if(accountToNote.hasPrivateAccess()) {
                bestAccountToNote = accountToNote;
                break;
            }
            if(accountToNote.isSubscribed) {
                bestAccountToNote = accountToNote;
                subscribedFound = true;
            }
            if(accountToNote.isTiedToThisAccount() && !subscribedFound) {
                bestAccountToNote = accountToNote;
            }
        }
        return bestAccountToNote.equals(EMPTY)
            ? myContext.accounts().getFirstPreferablySucceededForOrigin(noteForAnyAccount.origin)
            : bestAccountToNote.myAccount;
    }

    private static List<AccountToNote> getAccountsForNote(MyContext myContext, NoteForAnyAccount noteForAnyAccount) {
        return myContext.accounts().succeededForSameOrigin(noteForAnyAccount.origin).stream()
                .map(a -> new AccountToNote(noteForAnyAccount, a)).collect(toList());
    }

    public static AccountToNote getAccountToActOnNote(MyContext myContext, long activityId, long noteId,
                                                      @NonNull MyAccount myActingAccount,
                                                      @NonNull MyAccount currentAccount) {
        NoteForAnyAccount noteForAnyAccount = new NoteForAnyAccount(myContext, activityId, noteId);
        final List<AccountToNote> accountsForNote = getAccountsForNote(myContext, noteForAnyAccount);

        AccountToNote acting = accountsForNote.stream().filter(atn -> atn.myAccount.equals(myActingAccount))
                .findAny().orElse(EMPTY);
        if (!acting.equals(EMPTY)) return acting;

        AccountToNote bestAccountToNote = accountsForNote.stream().filter(atn -> atn.myAccount.equals(currentAccount))
                .findAny().orElse(EMPTY);
        for(AccountToNote accountToNote : accountsForNote) {
            if (!bestAccountToNote.myAccount.isValidAndSucceeded()) {
                bestAccountToNote = accountToNote;
            }
            if(accountToNote.hasPrivateAccess()) {
                bestAccountToNote = accountToNote;
                break;
            }
            if(accountToNote.isSubscribed && !bestAccountToNote.isSubscribed) {
                bestAccountToNote = accountToNote;
            }
            if(accountToNote.isTiedToThisAccount() && !bestAccountToNote.isTiedToThisAccount()) {
                bestAccountToNote = accountToNote;
            }
        }
        return bestAccountToNote;
    }

    public AccountToNote(@NonNull NoteForAnyAccount noteForAnyAccount, MyAccount myAccount) {
        this.noteForAnyAccount = noteForAnyAccount;
        this.myAccount = calculateMyAccount(noteForAnyAccount.origin, myAccount);
        if (this.myAccount.isValid()) {
            getData();
        }
    }

    @NonNull
    private MyAccount calculateMyAccount(Origin origin, MyAccount ma) {
        if (ma == null || !origin.isValid() || !ma.getOrigin().equals(origin) || ma.nonValid()) {
            return MyAccount.EMPTY;
        }
        return ma;
    }

    private void getData() {
        final String method = "getData";
        isRecipient = noteForAnyAccount.audience.findSame(this.myAccount.getActor()).isSuccess();
        isAuthor = (this.myAccount.getActorId() == noteForAnyAccount.author.actorId);
        isAuthorMySucceededMyAccount = isAuthor && myAccount.isValidAndSucceeded();
        ActorToNote actorToNote = MyQuery.favoritedAndReblogged(noteForAnyAccount.myContext,
                noteForAnyAccount.noteId, this.myAccount.getActorId());
        favorited = actorToNote.favorited;
        reblogged = actorToNote.reblogged;
        isSubscribed = actorToNote.subscribed;
        authorFollowed = myAccount.isFollowing(noteForAnyAccount.author);
        isActor = noteForAnyAccount.actor.actorId == this.myAccount.getActorId();
        actorFollowed = !isActor && (noteForAnyAccount.actor.actorId == noteForAnyAccount.author.actorId
                ? authorFollowed
                : myAccount.isFollowing(noteForAnyAccount.actor));
    }

    @NonNull
    public MyAccount getMyAccount() {
        return myAccount;
    }

    @NonNull
    public Actor getMyActor() {
        return myAccount.getActor();
    }

    public boolean isTiedToThisAccount() {
        return isRecipient || favorited || reblogged || isAuthor
                || actorFollowed || authorFollowed;
    }

    public boolean hasPrivateAccess() {
        return isRecipient || isAuthor;
    }

    public boolean isAuthorSucceededMyAccount() {
        return isAuthorMySucceededMyAccount;
    }

    @Override
    public String toString() {
        return "AccountToNote{" +
                "noteForAnyAccount=" + noteForAnyAccount +
                ", isAuthorMySucceededMyAccount=" + isAuthorMySucceededMyAccount +
                ", myAccount=" + myAccount.getAccountName() +
                ", accountActorId=" + this.myAccount.getActorId() +
                ", isSubscribed=" + isSubscribed +
                ", isAuthor=" + isAuthor +
                ", isActor=" + isActor +
                ", isRecipient=" + isRecipient +
                ", favorited=" + favorited +
                ", reblogged=" + reblogged +
                ", actorFollowed=" + actorFollowed +
                ", authorFollowed=" + authorFollowed +
                '}';
    }
}
