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
import org.andstatus.app.context.DemoData
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import org.junit.Assert
import java.util.concurrent.atomic.AtomicInteger

class DemoGnuSocialConversationInserter {
    private var iteration = 0
    private var conversationOid: String = ""
    private var accountActor: Actor = Actor.EMPTY
    private var origin: Origin = Origin.EMPTY
    fun insertConversation() {
        mySetup()
        addConversation()
    }

    private fun mySetup() {
        iteration = iterationCounter.incrementAndGet()
        conversationOid = java.lang.Long.toString(MyLog.uniqueCurrentTimeMS)
        origin = DemoData.demoData.getGnuSocialOrigin()
        Assert.assertTrue(DemoData.demoData.gnusocialTestOriginName + " exists", origin.isValid())
        Assert.assertNotSame("No host URL: $origin", "", origin.getHost())
        val myAccount: MyAccount = DemoData.demoData.getGnuSocialAccount()
        accountActor = myAccount.actor
        Assert.assertTrue("Should be fully defined $myAccount", accountActor.isFullyDefined())
        Assert.assertEquals("Inconsistent origin for $accountActor\n and $origin", accountActor.origin, origin)
    }

    private fun addConversation() {
        val author1 = actorFromOidAndAvatar("1",
                "https://raw.github.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png")
        val author2 = actorFromOidAndAvatar("2",
                "http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png")
        val author3 = actorFromOidAndAvatar("3",
                "http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif")
        val author4 = actorFromOidAndAvatar("4", "")
        val minus1 = buildActivity(author2, "Older one note", null, null)
        val selected = buildActivity(author1, "Selected note", minus1, null)
        selected.setSubscribedByMe(TriState.TRUE)
        val reply1 = buildActivity(author3, "Reply 1 to selected", selected, null)
        val reply2 = buildActivity(author2, "Reply 2 to selected is public", selected, null)
                .withVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS)
        val reply3 = buildActivity(author1, "Reply 3 to selected by the same author", selected, null)
        addActivity(selected)
        addActivity(reply3)
        addActivity(reply1)
        addActivity(reply2)
        DemoNoteInserter.assertStoredVisibility(reply2, Visibility.PUBLIC_AND_TO_FOLLOWERS)
        val reply4 = buildActivity(author4, "Reply 4 to Reply 1, " + DemoData.demoData.publicNoteText + " other author", reply1, null)
        addActivity(reply4)
        DemoNoteInserter.assertStoredVisibility(reply4, Visibility.PUBLIC)
        DemoNoteInserter.increaseUpdateDate(reply4).withVisibility(Visibility.PRIVATE)
        addActivity(reply4)
        DemoNoteInserter.assertStoredVisibility(reply4, Visibility.PRIVATE)
        val reply5 = buildActivity(author2, "Reply 5 to Reply 4", reply4, null)
        addWithMultipleAttachments(reply5)
        addWithMultipleImages(buildActivity(author3, "Reply 6 to Reply 4 - the second, with 2 images", reply4, null), 2)
        val reply7 = buildActivity(author1, "Reply 7 to Reply 2 is about "
                + DemoData.demoData.publicNoteText + " and something else", reply2, null)
                .withVisibility(Visibility.PUBLIC)
        addActivity(reply7)
        DemoNoteInserter.assertStoredVisibility(reply7, Visibility.PUBLIC)
        val reply8 = buildActivity(author4, "<b>Reply 8</b> to Reply 7", reply7, null)
        val reply9 = buildActivity(author2, "Reply 9 to Reply 7, 3 images", reply7, null)
        addWithMultipleImages(reply9, 3)
        val reply10 = buildActivity(author3, "Reply 10 to Reply 8, number of images: 1", reply8, null)
        addWithMultipleImages(reply10, 1)
        val reply11 = buildActivity(author2, "Reply 11 to Reply 7 with " + DemoData.demoData.globalPublicNoteText
                + " text", reply7, null)
                .withVisibility(Visibility.PUBLIC)
        addActivity(reply11)
        DemoNoteInserter.assertStoredVisibility(reply11, Visibility.PUBLIC)
        val reply12 = buildActivity(author2, "Reply 12 to Reply 7 reblogged by author1", reply7, null)
        DemoNoteInserter.onActivityS(reply12)
        val myReblogOf12 = DemoNoteInserter(accountActor).buildActivity(accountActor, ActivityType.ANNOUNCE, "")
        myReblogOf12.setActivity(reply12)
        DemoNoteInserter.onActivityS(myReblogOf12)
        val myReplyTo12 = buildActivity(accountActor, "My reply to 12 after my reblog", reply12, null)
        DemoNoteInserter.onActivityS(myReplyTo12)
        val likeOfMyReply = DemoNoteInserter(accountActor).buildActivity(author1, ActivityType.LIKE, "")
        likeOfMyReply.setActivity(myReplyTo12)
        addActivity(likeOfMyReply)
        DemoNoteInserter.assertInteraction(likeOfMyReply, NotificationEventType.LIKE, TriState.TRUE)
        val followOfMe = DemoNoteInserter(accountActor).buildActivity(author2, ActivityType.FOLLOW, "")
        followOfMe.setObjActor(accountActor)
        DemoNoteInserter.onActivityS(followOfMe)
        DemoNoteInserter.assertInteraction(followOfMe, NotificationEventType.FOLLOW, TriState.TRUE)
        val reply13 = buildActivity(author2, "Reply 13 to MyReply12", myReplyTo12, null)
        addActivity(reply13)
    }

    private fun addWithMultipleAttachments(activity: AActivity) {
        activity.addAttachment(
                Attachment.fromUriAndMimeType("https://gnusocial.example.com/api/statuses/update.json",
                        "application/json; charset=utf-8"))
        activity.addAttachment(
                Attachment.fromUriAndMimeType("https://www.w3.org/Protocols/rfc1341/7_2_Multipart.html",
                        "text/html; charset=iso-8859-1"))
        activity.addAttachment(
                Attachment.fromUriAndMimeType("https://www.w3.org/2008/site/images/logo-w3c-mobile-lg",
                        "image"))
        addActivity(activity)
        val attachments = activity.getNote().attachments
        Assert.assertEquals(attachments.toString(), 3, attachments.size().toLong())
        val attachment0 = attachments.list[0]
        val attachment2 = attachments.list[2]
        Assert.assertEquals("Image should be the first $attachments", 0,
                attachment2.getDownloadNumber())
        Assert.assertEquals("Download number should change $attachments", 2,
                attachment0.getDownloadNumber())
        Assert.assertEquals("Image attachment should be number 2 $attachments", "image",
                attachment2.mimeType)
    }

    private fun addWithMultipleImages(activity: AActivity, numberOfImages: Int) {
        for (ind in 0 until numberOfImages) {
            when (ind) {
                0 -> {
                    activity.addAttachment(Attachment.fromUriAndMimeType(
                            "https://thumbs.dreamstime.com/b/amazing-lake-arboretum-amazing-lake-arboretum-ataturk-arboretum-botanic-park-istanbul-160236958.jpg", "image"))
                    activity.addAttachment(Attachment.fromUriAndMimeType(
                            "https://www.w3.org/Protocols/rfc1341/7_2_Multipart.html",
                            "text/html; charset=iso-8859-1"))
                }
                1 -> {
                    activity.addAttachment(Attachment.fromUriAndMimeType(
                            "https://thumbs.dreamstime.com/b/tribal-women-farmers-paddy-rice-terraces-agricultural-fields-countryside-yen-bai-mountain-hills-valley-south-east-160537176.jpg",
                            "image"))
                    activity.addAttachment(
                            Attachment.fromUriAndMimeType("https://gnusocial.example.com/api/statuses/update.json",
                                    "application/json; charset=utf-8"))
                }
                2 -> {
                    activity.addAttachment(Attachment.fromUriAndMimeType(
                            "https://thumbs.dreamstime.com/b/concept-two-birds-chickadee-creeper-flew-branch-garden-under-banner-word-autumn-carved-red-maple-leaves-160997265.jpg",
                            "image"))
                    activity.addAttachment(
                            Attachment.fromUriAndMimeType("https://gnusocial.example.com/api/statuses/update2.json",
                                    "application/json; charset=utf-8"))
                }
            }
        }
        addActivity(activity)
        val attachments = activity.getNote().attachments
        Assert.assertEquals(attachments.toString(), (2 * numberOfImages).toLong(), attachments.size().toLong())
    }

    private fun actorFromOidAndAvatar(actorOid: String, avatarUrl: String?): Actor {
        val username = "actor$actorOid"
        val actor: Actor = Actor.fromOid(origin, actorOid)
        actor.setUsername(username)
        avatarUrl?.let { actor.setAvatarUrl(it) }
        actor.setProfileUrlToOriginUrl(origin.url)
        return actor.build()
    }

    private fun buildActivity(author: Actor, body: String?, inReplyToNote: AActivity?, noteOidIn: String?): AActivity {
        val activity = DemoNoteInserter(accountActor).buildActivity(author, "", body
                + if (inReplyToNote != null) " it$iteration" else "",
                inReplyToNote, noteOidIn, DownloadStatus.LOADED)
        activity.getNote().setConversationOid(conversationOid)
        return activity
    }

    private fun addActivity(activity: AActivity) {
        DemoNoteInserter.onActivityS(activity)
    }

    companion object {
        private val iterationCounter: AtomicInteger = AtomicInteger(0)
    }
}
