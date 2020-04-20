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
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.GregorianCalendar;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.net.social.Visibility.PUBLIC_AND_TO_FOLLOWERS;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DemoConversationInserter {
    private int iteration = 0;
    private MyAccount ma;
    private Actor accountActor = Actor.EMPTY;
    private String bodySuffix = "";

    public void insertConversation(String bodySuffixIn) {
        bodySuffix = StringUtil.isEmpty(bodySuffixIn)
                ? ""
                : " " + bodySuffixIn;
        iteration = demoData.conversationIterationCounter.incrementAndGet();
        ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(demoData.conversationAccountName + " exists", ma.isValid());
        accountActor = ma.getActor();
        insertAndTestConversation();
    }

    private void insertAndTestConversation() {
        assertEquals("Only PumpIo supported in this test", OriginType.PUMPIO, demoData.conversationOriginType);

        Actor author2 = buildActorFromOid(demoData.conversationAuthorSecondActorOid);
        author2.setAvatarUrl("http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png");

        Actor author3 = buildActorFromOid(demoData.conversationAuthorThirdActorOid);
        author3.setRealName("John Smith");
        author3.withUniqueName(demoData.conversationAuthorThirdUniqueName);
        author3.setHomepage("http://johnsmith.com/welcome");
        author3.setCreatedDate(new GregorianCalendar(2011,5,12).getTimeInMillis());
        author3.setSummary("I am an ordinary guy, interested in computer science");
        author3.setAvatarUrl("http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif");
        author3.build();

        Actor author4 = buildActorFromOid("acct:fourthWithoutAvatar@pump.example.com");
        author4.setRealName("Real Fourth");
        
        AActivity minus1 = buildActivity(author2, "", "Older one note", null, null);
        AActivity selected = buildActivity(getAuthor1(), "", "Selected note from Home timeline", minus1,
                iteration == 1 ? demoData.conversationEntryNoteOid : null);
        selected.setSubscribedByMe(TriState.TRUE);
        AActivity reply1 = buildActivity(author3, "The first reply", "Reply 1 to selected<br />" +
                        "&gt;&gt; Greater than<br />" +
                        "&lt;&lt; Less than, let&apos;s try!",
                selected, null);
        author3.isMyFriend = TriState.TRUE;

        AActivity reply1Copy = buildActivity(accountActor,
                Actor.fromOid(reply1.accountActor.origin, reply1.getAuthor().oid),
                "", "", AActivity.EMPTY,
                reply1.getNote().oid, DownloadStatus.UNKNOWN);
        AActivity reply12 = buildActivity(author2, "", "Reply 12 to 1 in Replies", reply1Copy, null);
        reply1.getNote().replies.add(reply12);

        AActivity privateReply2 = buildActivity(author2, "Private reply", "Reply 2 to selected is private",
                selected, null).withVisibility(Visibility.PRIVATE);
        addActivity(privateReply2);
        DemoNoteInserter.assertStoredVisibility(privateReply2, Visibility.PRIVATE);
        DemoNoteInserter.assertInteraction(privateReply2, NotificationEventType.PRIVATE, TriState.TRUE);
        assertEquals("Should be subscribed " + selected, TriState.TRUE,
                MyQuery.activityIdToTriState(ActivityTable.SUBSCRIBED, selected.getId()));
        DemoNoteInserter.assertInteraction(selected, NotificationEventType.HOME, TriState.TRUE);

        AActivity reply3 = buildActivity(getAuthor1(), "Another note title",
                "Reply 3 to selected by the same author", selected, null);
        reply3.addAttachment(
                Attachment.fromUri("http://www.publicdomainpictures.net/pictures/100000/nahled/broadcasting-tower-14081029181fC.jpg"));
        addActivity(reply3);
        addActivity(reply1);
        DemoNoteInserter.assertInteraction(reply1, NotificationEventType.EMPTY, TriState.FALSE);
        addActivity(privateReply2);
        AActivity reply4 = buildActivity(author4, "", "Reply 4 to Reply 1 other author", reply1, null);
        addActivity(reply4);

        DemoNoteInserter.increaseUpdateDate(reply4);
        addActivity(reply4.withVisibility(PUBLIC_AND_TO_FOLLOWERS));
        DemoNoteInserter.assertStoredVisibility(reply4, PUBLIC_AND_TO_FOLLOWERS);

        DemoConversationInserter.assertIfActorIsMyFriend(author3, true, ma);

        final String MENTIONS_NOTE_BODY = "@fourthWithoutAvatar@pump.example.com Reply 5 to Reply 4\n"
                + "@" + author3.getUsername()
                + " @unknownUser@example.com";
        AActivity reply5 = buildActivity(author2, "", MENTIONS_NOTE_BODY, reply4,
                iteration == 1 ? demoData.conversationMentionsNoteOid : null);
        addActivity(reply5);
        if (iteration == 1) {
            assertEquals(reply5.toString(), demoData.conversationMentionsNoteOid, reply5.getNote().oid);
        }
        assertThat("The user '" + author3.getUsername() + "' should be a recipient\n" + reply5.getNote(),
                reply5.getNote().audience().getNonSpecialActors(), hasItem(author3));
        assertThat("The user '" + author2.getUsername() + "' should not be a recipient\n" + reply5.getNote(),
                reply5.getNote().audience().getNonSpecialActors(), not(hasItem(author2)));

        Actor reblogger1 = buildActorFromOid("acct:reblogger@" + demoData.pumpioMainHost);
        reblogger1.setAvatarUrl("http://www.large-icons.com/stock-icons/free-large-android/48x48/dog-robot.gif");
        AActivity reblogOf5 = buildActivity(reblogger1, ActivityType.ANNOUNCE);
        reblogOf5.setNote(reply5.getNote().shallowCopy());
        reblogOf5.setSubscribedByMe(TriState.TRUE);
        addActivity(reblogOf5);

        final AActivity reply6 = buildActivity(author3, "", "Reply 6 to Reply 4 - the second", reply4, null);
        reply6.getNote().addFavoriteBy(accountActor, TriState.TRUE);
        addActivity(reply6);

        AActivity likeOf6 = buildActivity(author2, ActivityType.LIKE);
        likeOf6.setNote(reply6.getNote().shallowCopy());
        addActivity(likeOf6);

        AActivity reply7 = buildActivity(getAuthor1(), "", "Reply 7 to Reply 2 is about "
                + demoData.publicNoteText + " and something else", privateReply2, null)
            .withVisibility(PUBLIC_AND_TO_FOLLOWERS);
        addActivity(reply7);
        DemoNoteInserter.assertStoredVisibility(reply7, PUBLIC_AND_TO_FOLLOWERS);

        AActivity reply8 = buildActivity(author4, "", "<b>Reply 8</b> to Reply 7", reply7, null);
        AActivity reblogOfNewActivity8 = buildActivity(author3, ActivityType.ANNOUNCE);
        reblogOfNewActivity8.setActivity(reply8);
        addActivity(reblogOfNewActivity8);

        AActivity reply9 = buildActivity(author2, "", "Reply 9 to Reply 7", reply7, null);
        reply9.setSubscribedByMe(TriState.TRUE);
        reply9.addAttachment(Attachment.fromUri(
                "http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg"));
        addActivity(reply9);
        final AActivity duplicateOfReply9 = buildActivity(author4, "", "A duplicate of " + reply9.getNote().getContent(),
                null, null);
        duplicateOfReply9.setSubscribedByMe(TriState.TRUE);
        addActivity(duplicateOfReply9);

        AActivity myLikeOf9 =  AActivity.from(accountActor, ActivityType.LIKE) ;
        myLikeOf9.setActor(accountActor);
        myLikeOf9.setNote(reply9.getNote().shallowCopy());
        addActivity(myLikeOf9);

        // Note downloaded by another account
        final MyAccount ma2 = demoData.getMyAccount(demoData.conversationAccountSecondName);
        author3.isMyFriend = TriState.TRUE;
        author3.setUpdatedDate(MyLog.uniqueCurrentTimeMS());
        AActivity reply10 = buildActivity(ma2.getActor(), author3, "", "Reply 10 to Reply 8", reply8,
                null, DownloadStatus.LOADED);
        assertEquals("The third is a note Author", author3,  reply10.getAuthor());
        addActivity(reply10);
        author3.isMyFriend = TriState.UNKNOWN;
        author3.setUpdatedDate(MyLog.uniqueCurrentTimeMS());

        DemoConversationInserter.assertIfActorIsMyFriend(author3, true, ma2);

        AActivity anonymousReply = buildActivity(Actor.EMPTY, "", "Anonymous reply to Reply 10", reply10, null);
        addActivity(anonymousReply);

        AActivity reply11 = buildActivity(author2, "", "Reply 11 to Reply 7, " + demoData.globalPublicNoteText
                + " text", reply7, null)
            .withVisibility(PUBLIC_AND_TO_FOLLOWERS);
        addActivity(reply11);
        DemoNoteInserter.assertStoredVisibility(reply11, PUBLIC_AND_TO_FOLLOWERS);
        DemoNoteInserter.assertInteraction(reply11, NotificationEventType.EMPTY, TriState.FALSE);

        AActivity myReply13 = buildActivity(accountActor, "", "My reply 13 to Reply 2", privateReply2, null);
        AActivity reply14 = buildActivity(author3, "", "Publicly reply to my note 13", myReply13, null)
            .withVisibility(PUBLIC_AND_TO_FOLLOWERS);
        addActivity(reply14);
        DemoNoteInserter.assertStoredVisibility(reply14, PUBLIC_AND_TO_FOLLOWERS);
        DemoNoteInserter.assertInteraction(reply14, NotificationEventType.MENTION, TriState.TRUE);

        AActivity reblogOf14 = buildActivity(author2, ActivityType.ANNOUNCE);
        reblogOf14.setActivity(reply14);
        addActivity(reblogOf14);
        DemoNoteInserter.assertInteraction(reblogOf14, NotificationEventType.MENTION, TriState.TRUE);

        // Note: We cannot publicly Reblog private note. Yet
        AActivity reblogOfMyPrivate13 = buildActivity(author3, ActivityType.ANNOUNCE);
        reblogOfMyPrivate13.setActivity(myReply13);
        addActivity(reblogOfMyPrivate13);
        DemoNoteInserter.assertInteraction(reblogOfMyPrivate13, NotificationEventType.PRIVATE, TriState.TRUE);

        AActivity mentionOfAuthor3 = buildActivity(reblogger1, "",
                "@" + author3.getUsername() + " mention in reply to 4",
                reply4, iteration == 1 ? demoData.conversationMentionOfAuthor3Oid : null);
        addActivity(mentionOfAuthor3);

        AActivity followAuthor3 = buildActivity(author2, ActivityType.FOLLOW);
        followAuthor3.setObjActor(author3);
        addActivity(followAuthor3);
        DemoNoteInserter.assertInteraction(followAuthor3, NotificationEventType.EMPTY, TriState.FALSE);

        Actor notLoadedActor = Actor.fromOid(accountActor.origin, "acct:notloaded@someother.host."
        + demoData.testOriginParentHost);
        AActivity notLoaded1 = AActivity.newPartialNote(accountActor, notLoadedActor, MyLog.uniqueDateTimeFormatted());
        AActivity reply15 = buildActivity(author4, "", "Reply 15 to not loaded 1", notLoaded1, null);
        addActivity(reply15);

        AActivity followsMe1 = buildActivity(getAuthor1(), ActivityType.FOLLOW);
        followsMe1.setObjActor(accountActor);
        addActivity(followsMe1);
        DemoNoteInserter.assertInteraction(followsMe1, NotificationEventType.FOLLOW, TriState.TRUE);

        AActivity reply16 = buildActivity(author2, "", "<a href='" + author4.getProfileUrl() + "'>" +
                "@" + author4.getUsername() + "</a> Reply 16 to Reply 15", reply15, null);
        addActivity(reply16);

        AActivity followsMe3 = new DemoNoteInserter(accountActor).buildActivity(author3, ActivityType.FOLLOW, "");
        followsMe3.setObjActor(accountActor);
        DemoNoteInserter.onActivityS(followsMe3);

        AActivity followsAuthor4 = new DemoNoteInserter(accountActor).buildActivity(author3, ActivityType.FOLLOW, "");
        followsAuthor4.setObjActor(author4);
        DemoNoteInserter.onActivityS(followsAuthor4);
    }

    private Actor getAuthor1() {
        Actor author1 = buildActorFromOid(demoData.conversationEntryAuthorOid);
        author1.setAvatarUrl("https://raw.github.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png");
        return author1;
    }
    
    private Actor buildActorFromOid(String actorOid) {
        return new DemoNoteInserter(accountActor).buildActorFromOid(actorOid);
    }

    private AActivity buildActivity(Actor actor, ActivityType type) {
        return new DemoNoteInserter(accountActor).buildActivity(actor, type, "");
    }

    private AActivity buildActivity(Actor author, String name, String content, AActivity inReplyTo, String noteOidIn) {
        return buildActivity(accountActor, author, name, content, inReplyTo, noteOidIn, DownloadStatus.LOADED);
    }

    private AActivity buildActivity(Actor accountActor, Actor author, String name, String content, AActivity inReplyTo,
                                    String noteOidIn, DownloadStatus status) {
        return new DemoNoteInserter(accountActor).buildActivity(author, name,
            StringUtil.isEmpty(content) ? "" : content + (inReplyTo != null ? " it" + iteration : "") + bodySuffix,
            inReplyTo, noteOidIn, status);
    }

    private void addActivity(AActivity activity) {
        DemoNoteInserter.onActivityS(activity);
    }

    static void assertIfActorIsMyFriend(Actor actor, boolean isFriendOf, MyAccount ma) {
        boolean actualIsFriend = GroupMembership.isGroupMember(ma.getActor(), GroupType.FRIENDS, actor.actorId);
        assertEquals("Actor " + actor + " is a friend of " + ma, isFriendOf, actualIsFriend);
    }
}
