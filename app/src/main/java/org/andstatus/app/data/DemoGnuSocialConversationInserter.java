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

import androidx.annotation.Nullable;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.concurrent.atomic.AtomicInteger;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
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
        origin = demoData.getGnuSocialOrigin();
        assertTrue(demoData.gnusocialTestOriginName + " exists", origin.isValid());
        assertNotSame( "No host URL: " + origin, "", origin.getHost());
        final MyAccount myAccount = demoData.getGnuSocialAccount();
        accountActor = myAccount.getActor();
        assertTrue( "Should be fully defined " + myAccount, accountActor.isFullyDefined());
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
        AActivity reply2 = buildActivity(author2, "Reply 2 to selected is public", selected, null);
        addWithVisibility(reply2, Visibility.PUBLIC_AND_TO_FOLLOWERS);
        AActivity reply3 = buildActivity(author1, "Reply 3 to selected by the same author", selected, null);
        addActivity(selected);
        addActivity(reply3);
        addActivity(reply1);
        addActivity(reply2);

        AActivity reply4 = buildActivity(author4, "Reply 4 to Reply 1, " + demoData.publicNoteText + " other author", reply1, null);
        addActivity(reply4);
        DemoNoteInserter.increaseUpdateDate(reply4);
        addWithVisibility(reply4, Visibility.PRIVATE);

        final AActivity reply5 = buildActivity(author2, "Reply 5 to Reply 4", reply4, null);
        addWithMultipleAttachments(reply5);

        addWithMultipleImages(buildActivity(author3, "Reply 6 to Reply 4 - the second, with 2 images", reply4, null), 2);

        AActivity reply7 = buildActivity(author1, "Reply 7 to Reply 2 is about "
        + demoData.publicNoteText + " and something else", reply2, null);
        addWithVisibility(reply7, Visibility.PUBLIC_AND_TO_FOLLOWERS);
        
        AActivity reply8 = buildActivity(author4, "<b>Reply 8</b> to Reply 7", reply7, null);
        AActivity reply9 = buildActivity(author2, "Reply 9 to Reply 7, 3 images", reply7, null);
        addWithMultipleImages(reply9, 3);
        AActivity reply10 = buildActivity(author3, "Reply 10 to Reply 8, number of images: 1", reply8, null);
        addWithMultipleImages(reply10, 1);
        AActivity reply11 = buildActivity(author2, "Reply 11 to Reply 7 with " + demoData.globalPublicNoteText
                + " text", reply7, null);
        addWithVisibility(reply11, Visibility.PUBLIC_AND_TO_FOLLOWERS);

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
        DemoNoteInserter.assertInteraction(likeOfMyReply, NotificationEventType.LIKE, TriState.TRUE);

        AActivity followOfMe = new DemoNoteInserter(accountActor).buildActivity(author2, ActivityType.FOLLOW, "");
        followOfMe.setObjActor(accountActor);
        DemoNoteInserter.onActivityS(followOfMe);
        DemoNoteInserter.assertInteraction(followOfMe, NotificationEventType.FOLLOW, TriState.TRUE);

        AActivity reply13 = buildActivity(author2, "Reply 13 to MyReply12", myReplyTo12, null);
        addActivity(reply13);
    }

    private void addWithMultipleAttachments(AActivity activity) {
        activity.addAttachment(
                Attachment.fromUriAndMimeType("https://gnusocial.example.com/api/statuses/update.json",
                        "application/json; charset=utf-8"));
        activity.addAttachment(
                Attachment.fromUriAndMimeType("https://www.w3.org/Protocols/rfc1341/7_2_Multipart.html",
                        "text/html; charset=iso-8859-1"));
        activity.addAttachment(
                Attachment.fromUriAndMimeType("https://www.w3.org/2008/site/images/logo-w3c-mobile-lg",
                        "image"));
        addActivity(activity);

        final Attachments attachments = activity.getNote().attachments;
        assertEquals(attachments.toString(), 3, attachments.size());

        final Attachment attachment0 = attachments.list.get(0);
        final Attachment attachment2 = attachments.list.get(2);
        assertEquals("Image should be the first " + attachments, 0,
                attachment2.getDownloadNumber());
        assertEquals("Download number should change " + attachments, 2,
                attachment0.getDownloadNumber());
        assertEquals("Image attachment should be number 2 " + attachments, "image",
                attachment2.mimeType);
    }

    private void addWithMultipleImages(AActivity activity, int numberOfImages) {
        for (int ind = 0; ind < numberOfImages; ind++) {
            switch (ind) {
                case (0):
                    activity.addAttachment(Attachment.fromUriAndMimeType(
                            "https://thumbs.dreamstime.com/b/amazing-lake-arboretum-amazing-lake-arboretum-ataturk-arboretum-botanic-park-istanbul-160236958.jpg", "image"));
                    activity.addAttachment(Attachment.fromUriAndMimeType(
                            "https://www.w3.org/Protocols/rfc1341/7_2_Multipart.html",
                                    "text/html; charset=iso-8859-1"));
                    break;
                case (1):
                    activity.addAttachment(Attachment.fromUriAndMimeType(
                            "https://thumbs.dreamstime.com/b/tribal-women-farmers-paddy-rice-terraces-agricultural-fields-countryside-yen-bai-mountain-hills-valley-south-east-160537176.jpg",
                            "image"));
                    activity.addAttachment(
                            Attachment.fromUriAndMimeType("https://gnusocial.example.com/api/statuses/update.json",
                                    "application/json; charset=utf-8"));
                    break;
                case (2):
                    activity.addAttachment(Attachment.fromUriAndMimeType(
                            "https://thumbs.dreamstime.com/b/concept-two-birds-chickadee-creeper-flew-branch-garden-under-banner-word-autumn-carved-red-maple-leaves-160997265.jpg",
                            "image"));
                    activity.addAttachment(
                            Attachment.fromUriAndMimeType("https://gnusocial.example.com/api/statuses/update2.json",
                                    "application/json; charset=utf-8"));
                    break;
            }
        }
        addActivity(activity);
        final Attachments attachments = activity.getNote().attachments;
        assertEquals(attachments.toString(), 2 * numberOfImages, attachments.size());
    }

    private void addWithVisibility(AActivity activity, Visibility visibility) {
        activity.getNote().setVisibility(visibility);
        addActivity(activity);
        assertEquals("Visibility of: " + activity.getNote().getContent(),
                visibility, Visibility.fromNoteId(activity.getNote().noteId));
    }

    private Actor actorFromOidAndAvatar(String actorOid, @Nullable String avatarUrl) {
        String username = "actor" + actorOid;
        Actor actor = Actor.fromOid(origin, actorOid);
        actor.setUsername(username);
        if (avatarUrl != null) {
            actor.setAvatarUrl(avatarUrl);
        }
        actor.setProfileUrlToOriginUrl(origin.getUrl());
        return actor.build();
    }
    
    private AActivity buildActivity(Actor author, String body, AActivity inReplyToNote, String noteOidIn) {
        final AActivity activity = new DemoNoteInserter(accountActor).buildActivity(author, "", body
                        + (inReplyToNote != null ? " it" + iteration : ""),
                inReplyToNote, noteOidIn, DownloadStatus.LOADED);
        activity.getNote().setConversationOid(conversationOid);
        return activity;
    }
    
    private void addActivity(AActivity activity) {
        DemoNoteInserter.onActivityS(activity);
    }
}
