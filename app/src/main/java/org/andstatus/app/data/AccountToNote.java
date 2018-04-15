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

import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.origin.Origin;

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
    private final long accountActorId;
    public boolean isSubscribed = false;
    public boolean isAuthor = false;
    public boolean isActor = false;
    private boolean isRecipient = false;
    public boolean favorited = false;
    public boolean reblogged = false;
    public boolean actorFollowed = false;
    public boolean authorFollowed = false;

    public AccountToNote(@NonNull NoteForAnyAccount noteForAnyAccount, MyAccount myAccount) {
        this.noteForAnyAccount = noteForAnyAccount;
        this.myAccount = calculateMyAccount(noteForAnyAccount.origin, myAccount);
        this.accountActorId = this.myAccount.getActorId();
        if (this.myAccount.isValid()) {
            getData();
        }
    }

    @NonNull
    private MyAccount calculateMyAccount(Origin origin, MyAccount ma) {
        if (ma == null || !origin.isValid() || !ma.getOrigin().equals(origin) || !ma.isValid()) {
            return MyAccount.EMPTY;
        }
        return ma;
    }

    private void getData() {
        final String method = "getData";
        isRecipient = noteForAnyAccount.recipients.contains(accountActorId);
        isAuthor = (accountActorId == noteForAnyAccount.authorId);
        isAuthorMySucceededMyAccount = isAuthor && myAccount.isValidAndSucceeded();
        ActorToNote actorToNote = MyQuery.favoritedAndReblogged(noteForAnyAccount.myContext,
                noteForAnyAccount.noteId, accountActorId);
        favorited = actorToNote.favorited;
        reblogged = actorToNote.reblogged;
        isSubscribed = actorToNote.subscribed;
        authorFollowed = MyQuery.isFollowing(accountActorId, noteForAnyAccount.authorId);
        isActor = noteForAnyAccount.actorId == accountActorId;
        actorFollowed = !isActor && (noteForAnyAccount.actorId == noteForAnyAccount.authorId
                ? authorFollowed
                : MyQuery.isFollowing(accountActorId, noteForAnyAccount.actorId));
    }

    @NonNull
    public MyAccount getMyAccount() {
        return myAccount;
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
}
