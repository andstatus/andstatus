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

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.net.URL;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author yvolk@yurivolkov.com
 */
public class MbUser {
    public String oid="";
    private String userName="";
    private String webFingerId="";
    public String realName="";
    public String avatarUrl="";
    public String description="";
    public String homepage="";
    private Uri uri = Uri.EMPTY;
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
        return new MbUser();
    }
    
    private MbUser() {
        // Empty
    }
    
    public boolean isEmpty() {
        return TextUtils.isEmpty(oid) || originId==0;
    }

    @Override
    public String toString() {
        String str = MbUser.class.getSimpleName();
        String members = "oid=" + oid + "; originid=" + originId;
        if (!TextUtils.isEmpty(userName)) {
            members += "; username=" + userName;
        }
        if (!TextUtils.isEmpty(realName)) {
            members += "; realname=" + realName;
        }
        if (latestMessage != null) {
            members += "; latest message present";
        }
        return str + "{" + members + "}";
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
        fixWebFingerId();
    }

    public String getUrl() {
        return uri.toString();
    }

    public void setUrl(String url) {
        this.uri = UriUtils.fromString(url);
        fixWebFingerId();
    }

    public void setUrl(URL url) {
        uri = UriUtils.fromUrl(url);
        fixWebFingerId();
    }
    
    private void fixWebFingerId() {
        if (userName.contains("@") || UriUtils.isEmpty(uri)) {
            webFingerId = userName;
        } else {
            webFingerId = userName + "@" + uri.getHost();
        }
    }

    public String getWebFingerId() {
        return webFingerId;
    }
}
