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
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.widget.DuplicatesCollapsible;
import org.andstatus.app.widget.DuplicationLink;
import org.apache.commons.lang3.StringEscapeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MessageViewItem implements DuplicatesCollapsible<MessageViewItem> {

    long createdDate = 0;

    private long mMsgId;

    String body = "";

    boolean favorited = false;
    Map<Long, String> rebloggers = new HashMap<>();
    boolean reblogged = false;

    private long linkedUserId = 0;
    private List<TimelineViewItem> children = new ArrayList<>();

    long getMsgId() {
        return mMsgId;
    }

    void setMsgId(long mMsgId) {
        this.mMsgId = mMsgId;
    }

    public long getLinkedUserId() {
        return linkedUserId;
    }

    public void setLinkedUserId(long linkedUserId) {
        this.linkedUserId = linkedUserId;
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
                String thisBody = getCleanedBody(body);
                String otherBody = getCleanedBody(other.body);
                if (thisBody.equals(otherBody)) {
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

    @NonNull
    private String getCleanedBody(String body) {
        String out = MyHtml.fromHtml(body).toLowerCase();
        out = StringEscapeUtils.unescapeHtml4(out);
        return out.replaceAll("\n", " ").
                replaceAll("  ", " ").
                replaceFirst(".*(favorited something by.*)","$1");
    }

    private DuplicationLink duplicatesByFavoritedAndReblogged(MessageViewItem other) {
        DuplicationLink link;
        if (favorited != other.favorited) {
            link = favorited ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else if (reblogged != other.reblogged) {
            link = reblogged ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else if (getLinkedUserId() != other.getLinkedUserId()) {
            link = MyContextHolder.get().persistentAccounts().fromUserId(getLinkedUserId()).
                    compareTo(MyContextHolder.get().persistentAccounts().fromUserId(other.getLinkedUserId())) <= 0 ?
                    DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        } else {
            link = rebloggers.size() > other.rebloggers.size() ? DuplicationLink.IS_DUPLICATED : DuplicationLink.DUPLICATES;
        }
        return link;
    }
}
