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

import android.support.annotation.Nullable;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.concurrent.atomic.AtomicInteger;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class DemoGnuSocialConversationInserter {
    private static AtomicInteger iterationCounter = new AtomicInteger(0);
    private int iteration = 0;
    private String conversationOid = "";

    private Actor accountActor;
    private Origin origin;

    public void insertConversation() {
        mySetup();
        addConversation();
    }
    
    private void mySetup() {
        iteration = iterationCounter.incrementAndGet();
        conversationOid = Long.toString(MyLog.uniqueCurrentTimeMS());
        origin = MyContextHolder.get().persistentOrigins().fromName(demoData.gnusocialTestOriginName);
        assertTrue(demoData.gnusocialTestOriginName + " exists", origin.isValid());
        assertNotSame( "No host URL: " + origin, "", origin.getHost());
        final MyAccount myAccount = MyContextHolder.get().persistentAccounts()
                .fromAccountName(demoData.gnusocialTestAccountName);
        accountActor = myAccount.getActor();
        assertFalse( "Account actor is not defined " + myAccount + ", actor:" + accountActor,
                accountActor.isEmpty() || accountActor.isPartiallyDefined());
        assertEquals( "Inconsistent origin for " + accountActor + "\n and " + origin, accountActor.origin, origin);
    }
    
    private void addConversation() {
        Actor author1 = actorFromOidAndAvatar("1",
                "https://raw.github.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png");
        Actor author2 = actorFromOidAndAvatar("2",
                "http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png");
        Actor author3 = actorFromOidAndAvatar("3",
                "http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif");
        Actor author4 = actorFromOidAndAvatar("4", "");

        AActivity minus1 = buildActivity(author2, "Older one note", null, null);
        AActivity selected = buildActivity(author1, "Selected note", minus1,null);
        selected.setSubscribedByMe(TriState.TRUE);
        AActivity reply1 = buildActivity(author3, "Reply 1 to selected", selected, null);
        AActivity reply2 = buildActivity(author2, "Reply 2 to selected is non-private", selected, null);
        addPrivateNote(reply2, TriState.FALSE);
        AActivity reply3 = buildActivity(author1, "Reply 3 to selected by the same author", selected, null);
        addActivity(selected);
        addActivity(reply3);
        addActivity(reply1);
        addActivity(reply2);

        AActivity reply4 = buildActivity(author4, "Reply 4 to Reply 1, " + demoData.publicNoteText + " other author", reply1, null);
        addActivity(reply4);
        DemoNoteInserter.increaseUpdateDate(reply4);
        addPrivateNote(reply4, TriState.TRUE);

        addActivity(buildActivity(author2, "Reply 5 to Reply 4", reply4, null));
        addActivity(buildActivity(author3, "Reply 6 to Reply 4 - the second", reply4, null));

        AActivity reply7 = buildActivity(author1, "Reply 7 to Reply 2 is about "
        + demoData.publicNoteText + " and something else", reply2, null);
        addPrivateNote(reply7, TriState.FALSE);
        
        AActivity reply8 = buildActivity(author4, "<b>Reply 8</b> to Reply 7", reply7, null);
        AActivity reply9 = buildActivity(author2, "Reply 9 to Reply 7", reply7, null);
        addActivity(reply9);
        AActivity reply10 = buildActivity(author3, "Reply 10 to Reply 8", reply8, null);
        addActivity(reply10);
        AActivity reply11 = buildActivity(author2, "Reply 11 to Reply 7 with " + demoData.globalPublicNoteText + " text", reply7, null);
        addPrivateNote(reply11, TriState.FALSE);

        AActivity reply12 = buildActivity(author2, "Reply 12 to Reply 7 reblogged by author1", reply7, null);
        DemoNoteInserter.onActivityS(reply12);

        AActivity myReblogOf12 = new DemoNoteInserter(accountActor).buildActivity(accountActor, ActivityType.ANNOUNCE, "");
        myReblogOf12.setActivity(reply12);
        DemoNoteInserter.onActivityS(myReblogOf12);

        AActivity myReplyTo12 = buildActivity(accountActor, "My reply to 12 after my reblog", reply12, null);
        DemoNoteInserter.onActivityS(myReplyTo12);

        AActivity likeOfMyReply = new DemoNoteInserter(accountActor).buildActivity(author1, ActivityType.LIKE, "");
        likeOfMyReply.setActivity(myReplyTo12);
        addActivity(likeOfMyReply);
        DemoNoteInserter.assertNotified(likeOfMyReply, TriState.TRUE);

        AActivity followOfMe = new DemoNoteInserter(accountActor).buildActivity(author2, ActivityType.FOLLOW, "");
        followOfMe.setObjActor(accountActor);
        DemoNoteInserter.onActivityS(followOfMe);
        DemoNoteInserter.assertNotified(followOfMe, TriState.TRUE);

        AActivity reply13 = buildActivity(author2, "Reply 13 to MyReply12", myReplyTo12, null);
        addActivity(reply13);
    }

    private void addPrivateNote(AActivity activity, TriState isPrivate) {
        activity.getNote().setPrivate(isPrivate);
        addActivity(activity);
        assertEquals("Note is " + (isPrivate.equals(TriState.TRUE) ? "private" :
                        isPrivate.equals(TriState.FALSE) ? "non private" : "") + ": " + activity.getNote().getBody(),
                isPrivate, MyQuery.noteIdToTriState(NoteTable.PRIVATE, activity.getNote().noteId));
    }

    private Actor actorFromOidAndAvatar(String actorOid, @Nullable String avatarUrl) {
        String username = "actor" + actorOid;
        Actor actor = Actor.fromOriginAndActorOid(origin, actorOid);
        actor.setUsername(username);
        if (avatarUrl != null) {
            actor.avatarUrl = avatarUrl;
        }
        actor.setProfileUrl(origin.getUrl());
        return actor;
    }
    
    private AActivity buildActivity(Actor author, String body, AActivity inReplyToNote, String noteOidIn) {
        final AActivity activity = new DemoNoteInserter(accountActor).buildActivity(author, body
                        + (inReplyToNote != null ? " it" + iteration : ""),
                inReplyToNote, noteOidIn, DownloadStatus.LOADED);
        activity.getNote().setConversationOid(conversationOid);
        return activity;
    }
    
    private void addActivity(AActivity activity) {
        DemoNoteInserter.onActivityS(activity);
    }
}
