/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net;

public class MbTimelineItem {
    public enum ItemType {
        MESSAGE,
        USER,
        EMPTY
    }
    public TimelinePosition timelineItemPosition = null;
    public long timelineItemDate = 0;

    public MbMessage mbMessage = null;
    public MbUser mbUser = null;
    
    public ItemType getType() {
        if (mbMessage != null && !mbMessage.isEmpty()) {
            return ItemType.MESSAGE;
        } else if ( mbUser != null && !mbUser.isEmpty()) {
            return ItemType.USER;
        } else {
            return ItemType.EMPTY;
        }
    }

    public boolean isEmpty() {
        return getType() == ItemType.EMPTY;
    }
}
