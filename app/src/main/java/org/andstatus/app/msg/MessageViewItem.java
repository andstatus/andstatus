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

import org.andstatus.app.util.I18n;

import java.util.ArrayList;
import java.util.List;

public class MessageViewItem {

    private long mMsgId;
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
}
