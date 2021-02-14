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

import androidx.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Helper class to find out a relation of a Note to {@link #myAccount}
 * @author yvolk@yurivolkov.com
 */
public class NoteContextMenuData {
    private final static String TAG = NoteContextMenuData.class.getSimpleName();
    public static final NoteContextMenuData EMPTY = new NoteContextMenuData(NoteForAnyAccount.EMPTY, MyAccount.EMPTY);
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
        NoteContextMenuData bestFit = NoteContextMenuData.EMPTY;
        for(NoteContextMenuData menuData : getMenuData(myContext, noteForAnyAccount)) {
            if(menuData.hasPrivateAccess()) {
                bestFit = menuData;
                break;
            }
            if(menuData.isSubscribed) {
                bestFit = menuData;
                subscribedFound = true;
            }
            if(menuData.isTiedToThisAccount() && !subscribedFound) {
                bestFit = menuData;
            }
        }
        return bestFit.equals(EMPTY)
            ? myContext.accounts().getFirstPreferablySucceededForOrigin(noteForAnyAccount.origin)
            : bestFit.myAccount;
    }

    private static List<NoteContextMenuData> getMenuData(MyContext myContext, NoteForAnyAccount noteForAnyAccount) {
        return myContext.accounts().succeededForSameOrigin(noteForAnyAccount.origin).stream()
                .map(a -> new NoteContextMenuData(noteForAnyAccount, a)).collect(toList());
    }

    public static NoteContextMenuData getAccountToActOnNote(MyContext myContext, long activityId, long noteId,
                                                            @NonNull MyAccount myActingAccount,
                                                            @NonNull MyAccount currentAccount) {
        NoteForAnyAccount noteForAnyAccount = new NoteForAnyAccount(myContext, activityId, noteId);
        final List<NoteContextMenuData> menuDataList = getMenuData(myContext, noteForAnyAccount);

        NoteContextMenuData acting = menuDataList.stream().filter(atn -> atn.myAccount.equals(myActingAccount))
                .findAny().orElse(EMPTY);
        if (!acting.equals(EMPTY)) return acting;

        NoteContextMenuData bestFit = menuDataList.stream().filter(atn -> atn.myAccount.equals(currentAccount))
                .findAny().orElse(EMPTY);
        for(NoteContextMenuData menuData : menuDataList) {
            if (!bestFit.myAccount.isValidAndSucceeded()) {
                bestFit = menuData;
            }
            if(menuData.hasPrivateAccess()) {
                bestFit = menuData;
                break;
            }
            if(menuData.isSubscribed && !bestFit.isSubscribed) {
                bestFit = menuData;
            }
            if(menuData.isTiedToThisAccount() && !bestFit.isTiedToThisAccount()) {
                bestFit = menuData;
            }
        }
        return bestFit;
    }

    public NoteContextMenuData(@NonNull NoteForAnyAccount noteForAnyAccount, MyAccount myAccount) {
        this.noteForAnyAccount = noteForAnyAccount;
        this.myAccount = calculateMyAccount(noteForAnyAccount.origin, myAccount);
        if (this.myAccount.isValid()) {
            loadData();
        }
    }

    @NonNull
    private MyAccount calculateMyAccount(Origin origin, MyAccount ma) {
        if (ma == null || !origin.isValid() || !ma.getOrigin().equals(origin) || ma.nonValid()) {
            return MyAccount.EMPTY;
        }
        return ma;
    }

    private void loadData() {
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
        return TAG + "{" +
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
