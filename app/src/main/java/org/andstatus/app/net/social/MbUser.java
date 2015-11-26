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

package org.andstatus.app.net.social;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author yvolk@yurivolkov.com
 */
public class MbUser {
    private static final String USERNAME_REGEX = "[a-zA-Z_0-9]+([\\.\\-]*[a-zA-Z_0-9]+)*";
    public static final String WEBFINGER_ID_REGEX = USERNAME_REGEX + "@" + USERNAME_REGEX;
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
    public long userId = 0L;

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
        String members = "oid=" + oid + "; originId=" + originId;
        if (userId != 0) {
            members += "; id=" + userId;
        }
        if (!TextUtils.isEmpty(userName)) {
            members += "; username=" + userName;
        }
        if (!TextUtils.isEmpty(webFingerId)) {
            members += "; webFingerId=" + webFingerId;
        }
        if (!TextUtils.isEmpty(realName)) {
            members += "; realName=" + realName;
        }
        if (latestMessage != null) {
            members += "; latest message present";
        }
        return str + "{" + members + "}";
    }

    public String getUserName() {
        return userName;
    }

    public MbUser setUserName(String userName) {
        this.userName = userName;
        fixWebFingerId();
        return this;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MbUser mbUser = (MbUser) o;

        if (originId != mbUser.originId) return false;
        if (userId != mbUser.userId) return false;
        return oid.equals(mbUser.oid);
    }

    @Override
    public int hashCode() {
        int result = oid.hashCode();
        result = 31 * result + (int) (originId ^ (originId >>> 32));
        result = 31 * result + (int) (userId ^ (userId >>> 32));
        return result;
    }

    private void fixWebFingerId() {
        if (TextUtils.isEmpty(userName)) {
            // Do nothing
        } else if (userName.contains("@")) {
            webFingerId = userName;
        } else if (!UriUtils.isEmpty(uri)){
            MyContext myContect = MyContextHolder.get();
            if(myContect.isReady()) {
                Origin origin = myContect.persistentOrigins().fromId(originId);
                webFingerId = userName + "@" + origin.fixUriforPermalink(uri).getHost();
            } else {
                webFingerId = userName + "@" + uri.getHost();
            }
        }
    }

    public String getWebFingerId() {
        return webFingerId;
    }
    
    public static boolean isWebFingerIdValid(String webFingerId) {
        boolean ok = false;
        if (!TextUtils.isEmpty(webFingerId)) {
            ok = webFingerId.matches(WEBFINGER_ID_REGEX);
        }
        return ok;
    }

    public String getTempOid() {
        return getTempOid(webFingerId, userName);
    }

    public static String getTempOid(String validWebFingerId, String validUserName) {
        String userName = TextUtils.isEmpty(validWebFingerId) ? validUserName : validWebFingerId;
        return "andstatustemp:" + userName;
    }

    public static List<MbUser> fromBodyText(Origin origin, String textIn, boolean replyOnly) {
        final String SEPARATORS = ", ;'=`~!#$%^&*(){}[]";
        List<MbUser> users = new ArrayList<>();
        String text = MyHtml.fromHtml(textIn);
        while (!TextUtils.isEmpty(text)) {
            int atPos = text.indexOf('@');
            if (atPos < 0 || (atPos > 0 && replyOnly)) {
                break;
            }
            String validUserName = "";
            String validWebFingerId = "";
            int ind=atPos+1;
            for (; ind < text.length(); ind++) {
                if (SEPARATORS.indexOf(text.charAt(ind)) >= 0) {
                    break;
                }
                String userName = text.substring(atPos+1, ind + 1);
                if (origin.isUsernameValid(userName)) {
                    validUserName = userName;
                }
                if (isWebFingerIdValid(userName)) {
                    validWebFingerId = userName;
                }
            }
            if (ind < text.length()) {
                text = text.substring(ind);
            } else {
                text = "";
            }
            if (TextUtils.isEmpty(validWebFingerId) && TextUtils.isEmpty(validUserName)) {
                break;
            }

            String oid = MbUser.getTempOid(validWebFingerId, validUserName);
            long userId = 0;
            if (!TextUtils.isEmpty(validWebFingerId)) {
                userId = MyQuery.webFingerIdToId(origin.getId(), validWebFingerId);
            }
            if (userId == 0 && !TextUtils.isEmpty(validUserName)) {
                userId = MyQuery.userNameToId(origin.getId(), validUserName);
            }
            if (userId == 0 ) {
                userId = MyQuery.oidToId(MyDatabase.OidEnum.USER_OID, origin.getId(), oid);
            } else {
                oid = MyQuery.idToOid(MyDatabase.OidEnum.USER_OID, userId, 0);
            }
            MbUser mbUser = MbUser.fromOriginAndUserOid(origin.getId(), oid);
            mbUser.setUserName(TextUtils.isEmpty(validWebFingerId) ? validUserName : validWebFingerId);
            mbUser.userId = userId;
            if (!users.contains(mbUser)) {
                users.add(mbUser);
            }
            if (replyOnly) {
                break;
            }
        }
        return users;
    }
}
