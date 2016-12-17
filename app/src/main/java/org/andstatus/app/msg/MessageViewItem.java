/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.widget.DuplicatesCollapsible;
import org.andstatus.app.widget.DuplicationLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MessageViewItem implements DuplicatesCollapsible<MessageViewItem> {
    public static final int MIN_LENGTH_TO_COMPARE = 5;
    private MyContext myContext = MyContextHolder.get();
    long createdDate = 0;
    long sentDate = 0;

    DownloadStatus msgStatus = DownloadStatus.UNKNOWN;

    private long mMsgId;
    private long originId;

    String authorName = "";
    long authorId = 0;

    String recipientName = "";

    long inReplyToMsgId = 0;
    long inReplyToUserId = 0;
    String inReplyToName = "";

    String messageSource = "";

    String body = "";

    boolean favorited = false;
    Map<Long, String> rebloggers = new HashMap<>();
    boolean reblogged = false;

    AttachedImageFile attachedImageFile = AttachedImageFile.EMPTY;
    protected Drawable avatarDrawable = null;

    /** A message can be linked to any user, MyAccount or not */
    private long linkedUserId = 0;
    private MyAccount linkedMyAccount = MyAccount.getEmpty();

    private List<TimelineViewItem> children = new ArrayList<>();

    @NonNull
    public MyContext getMyContext() {
        return myContext;
    }

    public void setMyContext(MyContext myContext) {
        this.myContext = myContext;
    }

    long getMsgId() {
        return mMsgId;
    }

    void setMsgId(long mMsgId) {
        this.mMsgId = mMsgId;
    }

    public long getOriginId() {
        return originId;
    }

    void setOriginId(long originId) {
        this.originId = originId;
    }

    public long getLinkedUserId() {
        return linkedUserId;
    }

    public void setLinkedUserAndAccount(long linkedUserId) {
        this.linkedUserId = linkedUserId;
        linkedMyAccount = getMyContext().persistentAccounts().fromUserId(linkedUserId);
        if (!linkedMyAccount.isValid()) {
            linkedMyAccount = getMyContext().persistentAccounts().getFirstSucceededForOriginId(originId);
        }
    }

    public MyAccount getLinkedMyAccount() {
        return linkedMyAccount;
    }

    public boolean isLinkedToMyAccount() {
        return linkedUserId != 0 && linkedMyAccount.getUserId() == linkedUserId;
    }

    protected void setCollapsedStatus(Context context, StringBuilder messageDetails) {
        if (isCollapsed()) {
            I18n.appendWithSpace(messageDetails, "(+" + children.size() + ")");
        }
    }

    public void collapse(TimelineViewItem child) {
        this.children.addAll(child.getChildren());
        child.getChildren().clear();
        this.children.add(child);
    }

    public boolean isCollapsed() {
        return !children.isEmpty();
    }

    public List<TimelineViewItem> getChildren() {
        return children;
    }

    @Override
    public DuplicationLink duplicates(MessageViewItem other) {
        DuplicationLink link = DuplicationLink.NONE;
        if (other == null) {
            return link;
        }
        if (getMsgId() == other.getMsgId()) {
            link = duplicatesByFavoritedAndReblogged(other);
        }
        if (link == DuplicationLink.NONE) {
            if (Math.abs(createdDate - other.createdDate) < TimeUnit.HOURS.toMillis(24)) {
                String thisBody = MyHtml.getCleanedBody(body);
                String otherBody = MyHtml.getCleanedBody(other.body);
                if (thisBody.length() < MIN_LENGTH_TO_COMPARE ||
                        otherBody.length() < MIN_LENGTH_TO_COMPARE) {
                    // Too short to compare
                } else if (thisBody.equals(otherBody)) {
                    if (createdDate == other.createdDate) {
                        link = duplicatesByFavoritedAndReblogged(other);
                    } else if (createdDate < other.createdDate) {
                        link = DuplicationLink.IS_DUPLICATED;
                    } else {
                        link = DuplicationLink.DUPLICATES;
                    }
                } else if (thisBody.contains(otherBody)) {
                    link = DuplicationLink.DUPLICATES;
                } else if (otherBody.contains(thisBody)) {
                    link = DuplicationLink.IS_DUPLICATED;
                }
            }
        }
        return link;
    }

    private DuplicationLink duplicatesByFavoritedAndReblogged(MessageViewItem other) {
        DuplicationLink link;
        if (favorited != other.favorited) {
            link = favorited ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else if (reblogged != other.reblogged) {
            link = reblogged ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else if (!getLinkedMyAccount().equals(other.getLinkedMyAccount())) {
            link = getLinkedMyAccount().compareTo(other.getLinkedMyAccount()) <= 0 ?
                    DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else {
            link = rebloggers.size() > other.rebloggers.size() ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        }
        return link;
    }

    public boolean isReblogged() {
        return !rebloggers.isEmpty();
    }

    public StringBuilder getDetails(Context context) {
        StringBuilder builder = new StringBuilder(RelativeTime.getDifference(context, createdDate));
        setInReplyTo(context, builder);
        setRecipientName(context, builder);
        setMessageSource(context, builder);
        setMessageStatus(context, builder);
        setCollapsedStatus(context, builder);
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            I18n.appendWithSpace(builder, "(msgId=" + getMsgId() + ")");
        }
        return builder;
    }

    protected void setInReplyTo(Context context, StringBuilder messageDetails) {
        if (inReplyToMsgId != 0 && TextUtils.isEmpty(inReplyToName)) {
            inReplyToName = "...";
        }
        if (!TextUtils.isEmpty(inReplyToName)) {
            messageDetails.append(" ").append(String.format(
                    context.getText(R.string.message_source_in_reply_to).toString(),
                    inReplyToName));
        }
    }

    private void setRecipientName(Context context, StringBuilder messageDetails) {
        if (!TextUtils.isEmpty(recipientName)) {
            messageDetails.append(" " + String.format(
                    context.getText(R.string.message_source_to).toString(),
                    recipientName));
        }
    }

    private void setMessageSource(Context context, StringBuilder messageDetails) {
        if (!SharedPreferencesUtil.isEmpty(messageSource) && !"ostatus".equals(messageSource)
                && !"unknown".equals(messageSource)) {
            messageDetails.append(" " + String.format(
                    context.getText(R.string.message_source_from).toString(), messageSource));
        }
    }

    private void setMessageStatus(Context context, StringBuilder messageDetails) {
        if (msgStatus != DownloadStatus.LOADED) {
            messageDetails.append(" (").append(msgStatus.getTitle(context)).append(")");
        }
    }

    @NonNull
    public Drawable getAvatar() {
        return avatarDrawable == null ? AvatarFile.getDefaultDrawable() : avatarDrawable;
    }

    public AttachedImageFile getAttachedImageFile() {
        return attachedImageFile;
    }
}
