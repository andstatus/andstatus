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
import org.andstatus.app.util.SharedPreferencesUtil;
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
    // RegEx from http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    public static final String WEBFINGER_ID_REGEX = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    private static final String TEMP_OID_PREFIX = "andstatustemp:";
    public String oid="";
    private String userName="";
    private String webFingerId="";
    public String realName="";
    public String avatarUrl="";
    private String description="";
    private String homepage="";
    private Uri profileUri = Uri.EMPTY;
    public long createdDate = 0;
    public long updatedDate = 0;
    public MbMessage latestMessage = null;    

    public MbUser actor = null;
    public TriState followedByActor = TriState.UNKNOWN;
    
    // In our system
    public final long originId;
    public long userId = 0L;

    public static MbUser fromOriginAndUserOid(long originId, String userOid) {
        MbUser user = new MbUser(originId);
        user.oid = TextUtils.isEmpty(userOid) ? "" : userOid;
        return user;
    }
    
    public static MbUser getEmpty() {
        return new MbUser(0L);
    }
    
    private MbUser(long originId) {
        this.originId = originId;
    }
    
    public boolean isEmpty() {
        return originId==0 || (userId == 0 && !isOidReal(oid)
                && TextUtils.isEmpty(webFingerId) && TextUtils.isEmpty(userName));
    }

    public boolean isIdentified() {
        return userId != 0 && isOidReal();
    }

    public boolean isOidReal() {
        return isOidReal(oid);
    }

    public static boolean isOidReal(String oid) {
        return !SharedPreferencesUtil.isEmpty(oid) && !oid.startsWith(TEMP_OID_PREFIX);
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
        this.userName = TextUtils.isEmpty(userName) ? "" : userName.trim();
        fixWebFingerId();
        return this;
    }

    public String getProfileUrl() {
        return profileUri.toString();
    }

    public void setProfileUrl(String url) {
        this.profileUri = UriUtils.fromString(url);
        fixWebFingerId();
    }

    public void setProfileUrl(URL url) {
        profileUri = UriUtils.fromUrl(url);
        fixWebFingerId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MbUser that = (MbUser) o;
        if (originId != that.originId) return false;
        if (userId != 0 || that.userId != 0) {
            return userId == that.userId;
        }
        if (isOidReal(oid) || isOidReal(that.oid)) {
            return oid.equals(that.oid);
        }
        if (!TextUtils.isEmpty(getWebFingerId()) || !TextUtils.isEmpty(that.getWebFingerId())) {
            return getWebFingerId().equals(that.getWebFingerId());
        }
        return getUserName().equals(that.getUserName());
    }

    @Override
    public int hashCode() {
        int result = oid.hashCode();
        result = 31 * result + (int) (originId ^ (originId >>> 32));
        result = 31 * result + (int) (userId ^ (userId >>> 32));
        if (userId == 0) {
            if (isOidReal(oid)) {
                result = 31 * result + oid.hashCode();
            } else {
                result = 31 * result + getWebFingerId().hashCode();
                if (TextUtils.isEmpty(getWebFingerId())) {
                    result = 31 * result + getUserName().hashCode();
                }
            }
        }
        return result;
    }

    private void fixWebFingerId() {
        if (TextUtils.isEmpty(userName)) {
            // Do nothing
        } else if (userName.contains("@")) {
            setWebFingerId(userName);
        } else if (!UriUtils.isEmpty(profileUri)){
            MyContext myContext = MyContextHolder.get();
            if(myContext.isReady()) {
                Origin origin = myContext.persistentOrigins().fromId(originId);
                setWebFingerId(userName + "@" + origin.fixUriforPermalink(profileUri).getHost());
            } else {
                setWebFingerId(webFingerId = userName + "@" + profileUri.getHost());
            }
        }
    }

    public void setWebFingerId(String webFingerId) {
        if (isWebFingerIdValid(webFingerId)) {
            this.webFingerId = webFingerId;
        }
    }

    public String getWebFingerId() {
        return webFingerId;
    }

    public String getNamePreferablyWebFingerId() {
        String name = getWebFingerId();
        if (TextUtils.isEmpty(name)) {
            name = getUserName();
        }
        if (TextUtils.isEmpty(name)) {
            name = realName;
        }
        if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(oid)) {
            name = "oid: " + oid;
        }
        if (TextUtils.isEmpty(name)) {
            name = "id: " + userId;
        }
        return name;
    }

    public boolean isWebFingerIdValid() {
        return  isWebFingerIdValid(webFingerId);
    }

    public static boolean isWebFingerIdValid(String webFingerId) {
        boolean ok = false;
        if (!TextUtils.isEmpty(webFingerId)) {
            ok = webFingerId.matches(WEBFINGER_ID_REGEX);
        }
        return ok;
    }

    /**
     * Lookup the System's (AndStatus) id from the Originated system's id
     * @return userId
     */
    public long lookupUserId() {
        if (userId == 0) {
            if (isOidReal()) {
                userId = MyQuery.oidToId(MyDatabase.OidEnum.USER_OID, originId, oid);
            }
        }
        if (userId == 0 && isWebFingerIdValid()) {
            userId = MyQuery.webFingerIdToId(originId, webFingerId);
        }
        if (userId == 0 && !isWebFingerIdValid() && !TextUtils.isEmpty(userName)) {
            userId = MyQuery.userNameToId(originId, userName);
        }
        if (userId == 0) {
            userId = MyQuery.oidToId(MyDatabase.OidEnum.USER_OID, originId, getTempOid());
        }
        if (userId == 0 && hasAltTempOid()) {
            userId = MyQuery.oidToId(MyDatabase.OidEnum.USER_OID, originId, getAltTempOid());
        }
        return userId;
    }

    public boolean hasAltTempOid() {
        return !getTempOid().equals(getAltTempOid()) && !TextUtils.isEmpty(userName);
    }

    public String getTempOid() {
        return getTempOid(webFingerId, userName);
    }

    public String getAltTempOid() {
        return getTempOid("", userName);
    }

    public static String getTempOid(String webFingerId, String validUserName) {
        String oid = isWebFingerIdValid(webFingerId) ? webFingerId : validUserName;
        return TEMP_OID_PREFIX + oid;
    }

    public List<MbUser> fromBodyText(String textIn, boolean replyOnly) {
        final String SEPARATORS = ", ;'=`~!#$%^&*(){}[]/";
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(originId);
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
            if (MbUser.isWebFingerIdValid(validWebFingerId) || !TextUtils.isEmpty(validUserName)) {
                MbUser mbUser = MbUser.fromOriginAndUserOid(origin.getId(), "");
                if (!MbUser.isWebFingerIdValid(validWebFingerId)) {
                    // Try a host of the Author first
                    mbUser.userId = MyQuery.webFingerIdToId(origin.getId(), validUserName + "@" + getHost());
                    if (mbUser.userId == 0) {
                        // Next try host of this Social network
                        mbUser.userId = MyQuery.webFingerIdToId(origin.getId(), validUserName 
                            + "@" + origin.getUrl().getHost());
                    }
                    if (mbUser.userId != 0) {
                        validWebFingerId = validUserName + "@" + getHost();
                    }
                }
                mbUser.setWebFingerId(validWebFingerId);
                mbUser.setUserName(validUserName);
                mbUser.lookupUserId();
                if (!users.contains(mbUser)) {
                    users.add(mbUser);
                }
                if (replyOnly) {
                    break;
                }
            }
        }
        return users;
    }

    public String getHost() {
        int pos = getWebFingerId().indexOf('@');
        if (pos >= 0) {
            return getWebFingerId().substring(pos + 1);
        }
        if (!TextUtils.isEmpty(profileUri.getHost())) {
            return profileUri.getHost();
        }
        return "";
    }
    
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (!SharedPreferencesUtil.isEmpty(description)) {
            this.description = description;
        }
    }

    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        if (!SharedPreferencesUtil.isEmpty(homepage)) {
            this.homepage = homepage;
        }
    }
}
