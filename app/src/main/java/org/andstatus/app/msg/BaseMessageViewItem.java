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
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.ViewItem;
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
import org.andstatus.app.widget.DuplicationLink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BaseMessageViewItem implements ViewItem {
    private static final int MIN_LENGTH_TO_COMPARE = 5;
    private MyContext myContext = MyContextHolder.get();
    long updatedDate = 0;
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

    private String body = "";
    private String cleanedBody = "";

    boolean favorited = false;
    boolean isFavoritingAction = false;
    Map<Long, String> rebloggers = new HashMap<>();
    boolean reblogged = false;

    AttachedImageFile attachedImageFile = AttachedImageFile.EMPTY;
    AvatarFile avatarFile = AvatarFile.EMPTY;

    /** A message can be linked to any user, MyAccount or not */
    private long linkedUserId = 0;
    private MyAccount linkedMyAccount = MyAccount.EMPTY;

    private final List<ViewItem> children = new ArrayList<>();

    @NonNull
    public MyContext getMyContext() {
        return myContext;
    }

    public void setMyContext(MyContext myContext) {
        this.myContext = myContext;
    }

    public long getMsgId() {
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

    @NonNull
    public MyAccount getLinkedMyAccount() {
        return linkedMyAccount;
    }

    public boolean isLinkedToMyAccount() {
        return linkedUserId != 0 && linkedMyAccount.getUserId() == linkedUserId;
    }

    protected void setCollapsedStatus(Context context, StringBuilder messageDetails) {
        if (isCollapsed()) {
            I18n.appendWithSpace(messageDetails, "(+" + getChildren().size() + ")");
        }
    }

    @Override
    public Collection<ViewItem> getChildren() {
        return children;
    }

    @Override
    public DuplicationLink duplicates(ViewItem otherIn) {

        DuplicationLink link = DuplicationLink.NONE;
        if (otherIn == null || !BaseMessageViewItem.class.isAssignableFrom(otherIn.getClass())) {
            return link;
        }
        BaseMessageViewItem other = (BaseMessageViewItem) otherIn;
        if (getMsgId() == other.getMsgId()) {
            link = duplicatesByFavoritedAndReblogged(other);
        }
        if (link == DuplicationLink.NONE) {
            if (Math.abs(updatedDate - other.updatedDate) < TimeUnit.HOURS.toMillis(24)) {
                if (cleanedBody.length() < MIN_LENGTH_TO_COMPARE ||
                        other.cleanedBody.length() < MIN_LENGTH_TO_COMPARE) {
                    // Too short to compare
                } else if (cleanedBody.equals(other.cleanedBody)) {
                    if (updatedDate == other.updatedDate) {
                        link = duplicatesByFavoritedAndReblogged(other);
                    } else if (updatedDate < other.updatedDate) {
                        link = DuplicationLink.IS_DUPLICATED;
                    } else {
                        link = DuplicationLink.DUPLICATES;
                    }
                } else if (cleanedBody.contains(other.cleanedBody)) {
                    link = DuplicationLink.DUPLICATES;
                } else if (other.cleanedBody.contains(cleanedBody)) {
                    link = DuplicationLink.IS_DUPLICATED;
                }
            }
        }
        return link;
    }

    private DuplicationLink duplicatesByFavoritedAndReblogged(BaseMessageViewItem other) {
        DuplicationLink link;
        if (favorited != other.favorited) {
            link = favorited ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else if (isFavoritingAction != other.isFavoritingAction) {
            link = other.isFavoritingAction ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
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
        StringBuilder builder = new StringBuilder(RelativeTime.getDifference(context, updatedDate));
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

    public AttachedImageFile getAttachedImageFile() {
        return attachedImageFile;
    }

    public BaseMessageViewItem setBody(String body) {
        this.body = body;
        this.isFavoritingAction = MyHtml.isFavoritingAction(body);
        cleanedBody = MyHtml.getCleanedBody(body);
        return this;
    }

    public String getBody() {
        return body;
    }

    @Override
    public long getId() {
        return getMsgId();
    }

    @Override
    public long getDate() {
        return sentDate;
    }


    @NonNull
    public Pair<ViewItem, Boolean> fromCursor(Cursor cursor, KeywordsFilter keywordsFilter,
                                                   KeywordsFilter searchQuery, boolean hideRepliesNotToMeOrFriends) {
        MessageViewItem item = MessageViewItem.fromCursorRow(getMyContext(), cursor);
        String body = MyHtml.getBodyToSearch(item.getBody());
        boolean skip = keywordsFilter.matchedAny(body);
        if (!skip && !searchQuery.isEmpty()) {
            skip = !searchQuery.matchedAll(body);
        }
        if (!skip && hideRepliesNotToMeOrFriends && item.inReplyToUserId != 0) {
            skip = !MyContextHolder.get().persistentAccounts().isMeOrMyFriend(item.inReplyToUserId);
        }
        return new Pair(item, skip);
    }

    @Override
    public String toString() {
        return "Message " + body;
    }
}
