/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.timeline.DuplicationLink;
import org.andstatus.app.timeline.TimelineFilter;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class BaseMessageViewItem<T extends BaseMessageViewItem<T>> extends ViewItem<T> {
    private static final int MIN_LENGTH_TO_COMPARE = 5;
    private MyContext myContext = MyContextHolder.get();
    long updatedDate = 0;
    long activityInsDate = 0;

    public DownloadStatus msgStatus = DownloadStatus.UNKNOWN;

    private long mMsgId;
    private long originId;

    String authorName = "";
    long authorId = 0;

    String recipientName = "";

    public long inReplyToMsgId = 0;
    long inReplyToUserId = 0;
    String inReplyToName = "";

    String messageSource = "";

    private String body = "";
    String cleanedBody = "";

    boolean favorited = false;
    boolean isFavoritingAction = false;
    Map<Long, String> rebloggers = new HashMap<>();
    boolean reblogged = false;

    AttachedImageFile attachedImageFile = AttachedImageFile.EMPTY;
    AvatarFile avatarFile = AvatarFile.EMPTY;

    /** A message can be linked to any user, MyAccount or not */
    private long linkedUserId = 0;
    private MyAccount linkedMyAccount = MyAccount.EMPTY;

    protected BaseMessageViewItem(boolean isEmpty) {
        super(isEmpty);
    }

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

    void setLinkedUserAndAccount(long linkedUserId) {
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

    private void setCollapsedStatus(StringBuilder messageDetails) {
        if (isCollapsed()) {
            I18n.appendWithSpace(messageDetails, "(+" + getChildren().size() + ")");
        }
    }

    @Override
    @NonNull
    public DuplicationLink duplicates(@NonNull T other) {
        if (isEmpty() || other.isEmpty()) return DuplicationLink.NONE;
        return (getMsgId() == other.getMsgId()) ? duplicatesByFavoritedAndReblogged(other) : duplicatesByOther(other);
    }

    @NonNull
    private DuplicationLink duplicatesByFavoritedAndReblogged(@NonNull T other) {
        if (favorited != other.favorited) {
            return favorited ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else if (isFavoritingAction != other.isFavoritingAction) {
            return other.isFavoritingAction ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else if (reblogged != other.reblogged) {
            return reblogged ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else if (!getLinkedMyAccount().equals(other.getLinkedMyAccount())) {
            return getLinkedMyAccount().compareTo(other.getLinkedMyAccount()) <= 0 ?
                    DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        }
        return rebloggers.size() > other.rebloggers.size() ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
    }

    @NonNull
    private DuplicationLink duplicatesByOther(@NonNull T other) {
        if (Math.abs(updatedDate - other.updatedDate) >= TimeUnit.HOURS.toMillis(24)
                || isTooShortToCompare() || other.isTooShortToCompare()) return DuplicationLink.NONE;
        if (cleanedBody.equals(other.cleanedBody)) {
            if (updatedDate == other.updatedDate) {
                return duplicatesByFavoritedAndReblogged(other);
            } else if (updatedDate < other.updatedDate) {
                return DuplicationLink.IS_DUPLICATED;
            } else {
                return DuplicationLink.DUPLICATES;
            }
        } else if (cleanedBody.contains(other.cleanedBody)) {
            return DuplicationLink.DUPLICATES;
        } else if (other.cleanedBody.contains(cleanedBody)) {
            return DuplicationLink.IS_DUPLICATED;
        }
        return DuplicationLink.NONE;
    }

    boolean isTooShortToCompare() {
        return cleanedBody.length() < MIN_LENGTH_TO_COMPARE;
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
        setCollapsedStatus(builder);
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
        return updatedDate;
    }

    @Override
    public boolean matches(TimelineFilter filter) {
        if (!filter.keywordsFilter.isEmpty() || !filter.searchQuery.isEmpty()) {
            String bodyToSearch = MyHtml.getBodyToSearch(getBody());
            if (filter.keywordsFilter.matchedAny(bodyToSearch)) return false;
            if (!filter.searchQuery.isEmpty() && !filter.searchQuery.matchedAll(bodyToSearch)) return false;
        }
        if (filter.hideRepliesNotToMeOrFriends && inReplyToUserId != 0
                && !MyContextHolder.get().persistentAccounts().isMeOrMyFriend(inReplyToUserId)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "Message " + body;
    }

    public void hideActor(long userId) {
        rebloggers.remove(userId);
    }
}
