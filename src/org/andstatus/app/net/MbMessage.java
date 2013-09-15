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

import android.text.TextUtils;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author Yuri Volkov
 */
public class MbMessage {
    private boolean isEmpty = false;
    
    public String oid="";
    public long sentDate = 0;
    public MbUser sender = null;
    public MbUser recipient = null; //TODO: Multiple recipients needed?!
    public String body = "";
    public MbMessage rebloggedMessage = null;
    public MbMessage inReplyToMessage = null;
    public String via = "";

    /**
     * Some additional attributes may appear from the Reader's
     * point of view (usually - from the point of view of the authenticated user)
     */
    public MbUser reader = null;
    public Boolean favoritedByReader = null;
    
    // In our system
    public long originId = 0L;

    
    public static MbMessage fromOriginAndOid(long originId, String oid) {
        MbMessage message = new MbMessage();
        message.originId = originId;
        message.oid = oid;
        return message;
    }
    
    public static MbMessage getEmpty() {
        MbMessage message = new MbMessage();
        return message;
    }

    public MbMessage markAsEmpty() {
        isEmpty = true;
        return this;
    }
    
    private MbMessage() {}
    
    public boolean isEmpty() {
        return (this.isEmpty || TextUtils.isEmpty(oid) || originId==0);
    }
}
