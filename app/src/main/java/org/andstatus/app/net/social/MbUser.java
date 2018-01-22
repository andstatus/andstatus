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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author yvolk@yurivolkov.com
 */
public class MbUser implements Comparable<MbUser> {
    public static final MbUser EMPTY = new MbUser(Origin.EMPTY, "");
    // RegEx from http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    public static final String WEBFINGER_ID_REGEX = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
    public final String oid;
    private String userName = "";

    private String webFingerId = "";
    private boolean isWebFingerIdValid = false;

    private String realName = "";
    private String description = "";
    public String location = "";

    private Uri profileUri = Uri.EMPTY;
    private String homepage = "";
    public String avatarUrl = "";
    public String bannerUrl = "";

    public long msgCount = 0;
    public long favoritesCount = 0;
    public long followingCount = 0;
    public long followersCount = 0;

    private long createdDate = 0;
    private long updatedDate = 0;

    private MbActivity latestActivity = null;

    // Hack for Twitter like origins...
    public TriState followedByMe = TriState.UNKNOWN;

    // In our system
    @NonNull
    public final Origin origin;
    public long userId = 0L;

    @NonNull
    public static MbUser fromOriginAndUserOid(@NonNull Origin origin, String userOid) {
        return new MbUser(origin, userOid);
    }

    public static MbUser fromOriginAndUserId(@NonNull Origin origin, long userId) {
        MbUser user = new MbUser(origin, "");
        user.userId = userId;
        return user;
    }

    private MbUser(Origin origin, String userOid) {
        this.origin = origin;
        this.oid = TextUtils.isEmpty(userOid) ? "" : userOid;
    }

    @NonNull
    public MbActivity update(MbUser accountUser) {
        return update(accountUser, accountUser);
    }

    @NonNull
    public MbActivity update(MbUser accountUser, @NonNull MbUser actor) {
        return act(accountUser, actor, MbActivityType.UPDATE);
    }

    @NonNull
    public MbActivity act(MbUser accountUser, @NonNull MbUser actor, @NonNull MbActivityType activityType) {
        if (this == EMPTY || accountUser == EMPTY || actor == EMPTY) {
            return MbActivity.EMPTY;
        }
        MbActivity mbActivity = MbActivity.from(accountUser, activityType);
        mbActivity.setActor(actor);
        mbActivity.setUser(this);
        return mbActivity;
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return this == EMPTY || !origin.isValid() || (userId == 0 && UriUtils.nonRealOid(oid)
                && TextUtils.isEmpty(webFingerId) && TextUtils.isEmpty(userName));
    }

    public boolean isPartiallyDefined() {
        return !origin.isValid() || UriUtils.nonRealOid(oid) || TextUtils.isEmpty(webFingerId)
                || TextUtils.isEmpty(userName);
    }

    public boolean isIdentified() {
        return userId != 0 && isOidReal();
    }

    public boolean isOidReal() {
        return UriUtils.isRealOid(oid);
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "MbUser:EMPTY";
        }
        String str = MbUser.class.getSimpleName();
        String members = "oid=" + oid + "; origin=" + origin.getName();
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
        if (!Uri.EMPTY.equals(profileUri)) {
            members += "; profileUri=" + profileUri.toString();
        }
        if (hasLatestMessage()) {
            members += "; latest message present";
        }
        return str + "{" + members + "}";
    }

    public String getUserName() {
        return userName;
    }

    public MbUser setUserName(String userName) {
        if (this == EMPTY) {
            throw new IllegalStateException("Cannot set username of EMPTY MbUser");
        }
        this.userName = SharedPreferencesUtil.isEmpty(userName) ? "" : userName.trim();
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
        if (!origin.equals(that.origin)) return false;
        if (userId != 0 || that.userId != 0) {
            return userId == that.userId;
        }
        if (UriUtils.isRealOid(oid) || UriUtils.isRealOid(that.oid)) {
            return oid.equals(that.oid);
        }
        if (!TextUtils.isEmpty(getWebFingerId()) || !TextUtils.isEmpty(that.getWebFingerId())) {
            return getWebFingerId().equals(that.getWebFingerId());
        }
        return getUserName().equals(that.getUserName());
    }

    @Override
    public int hashCode() {
        int result = origin.hashCode ();
        if (userId != 0) {
            return 31 * result + (int) (userId ^ (userId >>> 32));
        }
        if (UriUtils.isRealOid(oid)) {
            return 31 * result + oid.hashCode();
        }
        if (!TextUtils.isEmpty(getWebFingerId())) {
            return 31 * result + getWebFingerId().hashCode();
        }
        return 31 * result + getUserName().hashCode();
    }

    /** Doesn't take origin into account */
    public boolean isSameUser(MbUser that) {
        return  isSameUser(that, false);
    }

    private boolean isSameUser(MbUser that, boolean sameOriginOnly) {
        if (this == that) return true;
        if (that == null) return false;
        if (userId != 0 && that.userId != 0) {
            if (userId == that.userId) return true;
        }
        if (origin.equals(that.origin)) {
            if (UriUtils.isRealOid(oid) && UriUtils.isRealOid(that.oid)) {
                return oid.equals(that.oid);
            }
        } else if (sameOriginOnly) {
            return false;
        }
        return isWebFingerIdValid && that.isWebFingerIdValid && webFingerId.equals(that.webFingerId);
    }

    private void fixWebFingerId() {
        if (TextUtils.isEmpty(userName)) {
            // Do nothing
        } else if (userName.contains("@")) {
            setWebFingerId(userName);
        } else if (!UriUtils.isEmpty(profileUri)){
            if(origin.isValid()) {
                setWebFingerId(userName + "@" + origin.fixUriforPermalink(profileUri).getHost());
            } else {
                setWebFingerId(webFingerId = userName + "@" + profileUri.getHost());
            }
        }
    }

    public void setWebFingerId(String webFingerId) {
        if (isWebFingerIdValid(webFingerId)) {
            this.webFingerId = webFingerId;
            isWebFingerIdValid = true;
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
        return  isWebFingerIdValid;
    }

    static boolean isWebFingerIdValid(String webFingerId) {
        return StringUtils.nonEmpty(webFingerId) && webFingerId.matches(WEBFINGER_ID_REGEX);
    }

    /**
     * Lookup the System's (AndStatus) id from the Originated system's id
     * @return userId
     */
    public long lookupUserId() {
        if (userId == 0) {
            if (isOidReal()) {
                userId = MyQuery.oidToId(OidEnum.USER_OID, origin.getId(), oid);
            }
        }
        if (userId == 0 && isWebFingerIdValid()) {
            userId = MyQuery.webFingerIdToId(origin.getId(), webFingerId);
        }
        if (userId == 0 && !isWebFingerIdValid() && !TextUtils.isEmpty(userName)) {
            userId = MyQuery.userNameToId(origin.getId(), userName);
        }
        if (userId == 0) {
            userId = MyQuery.oidToId(OidEnum.USER_OID, origin.getId(), getTempOid());
        }
        if (userId == 0 && hasAltTempOid()) {
            userId = MyQuery.oidToId(OidEnum.USER_OID, origin.getId(), getAltTempOid());
        }
        return userId;
    }

    public boolean hasAltTempOid() {
        return !getTempOid().equals(getAltTempOid()) && !TextUtils.isEmpty(userName);
    }

    public boolean hasLatestMessage() {
        return latestActivity != null && !latestActivity.isEmpty() ;
    }

    public String getTempOid() {
        return getTempOid(webFingerId, userName);
    }

    public String getAltTempOid() {
        return getTempOid("", userName);
    }

    public static String getTempOid(String webFingerId, String validUserName) {
        String oid = isWebFingerIdValid(webFingerId) ? webFingerId : validUserName;
        return UriUtils.TEMP_OID_PREFIX + oid;
    }

    public List<MbUser> extractUsersFromBodyText(String textIn, boolean replyOnly) {
        final String SEPARATORS = ", ;'=`~!#$%^&*(){}[]/";
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
                MbUser mbUser = MbUser.fromOriginAndUserOid(origin, "");
                if (!MbUser.isWebFingerIdValid(validWebFingerId)) {
                    // Try a host of the Author first
                    mbUser.userId = MyQuery.webFingerIdToId(origin.getId(), validUserName + "@" + getHost());
                    if (mbUser.userId == 0 && (origin.getUrl() != null)) {
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

    public MbUser setDescription(String description) {
        if (!SharedPreferencesUtil.isEmpty(description)) {
            this.description = description;
        }
        return this;
    }

    public String getHomepage() {
        return homepage;
    }

    public void setHomepage(String homepage) {
        if (!SharedPreferencesUtil.isEmpty(homepage)) {
            this.homepage = homepage;
        }
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        if (!SharedPreferencesUtil.isEmpty(realName)) {
            this.realName = realName;
        }
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    @Override
    public int compareTo(MbUser another) {
        if (userId != 0 && another.userId != 0) {
            if (userId == another.userId) {
                return 0;
            }
            return origin.getId() > another.origin.getId() ? 1 : -1;
        }
        if (origin.getId() != another.origin.getId()) {
            return origin.getId() > another.origin.getId() ? 1 : -1;
        }
        return oid.compareTo(another.oid);
    }

    public MbActivity getLatestActivity() {
        return latestActivity;
    }

    public void setLatestActivity(@NonNull MbActivity latestActivity) {
        this.latestActivity = latestActivity;
        if (this.latestActivity.getAuthor().isEmpty()) {
            this.latestActivity.setAuthor(this);
        }
    }

    public String toUserTitle(boolean showWebFingerId) {
        StringBuilder builder = new StringBuilder();
        if (showWebFingerId && !TextUtils.isEmpty(getWebFingerId())) {
            builder.append(getWebFingerId());
        } else if (!TextUtils.isEmpty(getUserName())) {
            builder.append("@" + getUserName());
        }
        if (!TextUtils.isEmpty(getRealName())) {
            I18n.appendWithSpace(builder, "(" + getRealName() + ")");
        }
        return builder.toString();
    }

    public String getTimelineUserName() {
        return MyQuery.userIdToWebfingerId(userId);
    }
}
