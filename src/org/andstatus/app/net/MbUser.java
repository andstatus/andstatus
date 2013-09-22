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

import org.andstatus.app.util.TriState;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author Yuri Volkov
 */
public class MbUser {
    public String oid="";
    public String userName="";
    public String realName="";
    public String avatarUrl="";
    public String description="";
    public String homepage="";
    public long createdDate = 0;
    public long updatedDate = 0;
    public MbMessage latestMessage = null;    

    public MbUser actor = null;
    public TriState followedByActor = TriState.UNKNOWN;
    
    // In our system
    public long originId = 0L;
    
    public static MbUser fromOriginAndUserOid(long originId, String userOid) {
        MbUser user = new MbUser();
        user.originId = originId;
        user.oid = userOid;
        return user;
    }
    
    public static MbUser getEmpty() {
        MbUser user = new MbUser();
        return user;
    }
    
    private MbUser() {}
    
    public boolean isEmpty() {
        return ((TextUtils.isEmpty(userName) && TextUtils.isEmpty(oid)) || originId==0);
    }
}
