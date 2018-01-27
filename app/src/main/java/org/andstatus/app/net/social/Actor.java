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
import org.andstatus.app.util.UrlUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 'Mb' stands for "Microblogging system" 
 * @author yvolk@yurivolkov.com
 */
public class Actor implements Comparable<Actor> {
    public static final Actor EMPTY = new Actor(Origin.EMPTY, "");
    // RegEx from http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    public static final String WEBFINGER_ID_REGEX = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
    @NonNull
    public final String oid;
    private String actorName = "";

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

    private AActivity latestActivity = null;

    // Hack for Twitter like origins...
    public TriState followedByMe = TriState.UNKNOWN;

    // In our system
    @NonNull
    public final Origin origin;
    public long actorId = 0L;

    @NonNull
    public static Actor fromOriginAndActorOid(@NonNull Origin origin, String actorOid) {
        return new Actor(origin, actorOid);
    }

    public static Actor fromOriginAndActorId(@NonNull Origin origin, long actorId) {
        Actor actor = new Actor(origin, "");
        actor.actorId = actorId;
        return actor;
    }

    private Actor(@NonNull Origin origin, String actorOid) {
        this.origin = origin;
        this.oid = TextUtils.isEmpty(actorOid) ? "" : actorOid;
    }

    @NonNull
    public AActivity update(Actor accountActor) {
        return update(accountActor, accountActor);
    }

    @NonNull
    public AActivity update(Actor accountActor, @NonNull Actor actor) {
        return act(accountActor, actor, ActivityType.UPDATE);
    }

    @NonNull
    public AActivity act(Actor accountActor, @NonNull Actor actor, @NonNull ActivityType activityType) {
        if (this == EMPTY || accountActor == EMPTY || actor == EMPTY) {
            return AActivity.EMPTY;
        }
        AActivity mbActivity = AActivity.from(accountActor, activityType);
        mbActivity.setActor(actor);
        mbActivity.setObjActor(this);
        return mbActivity;
    }

    public boolean nonEmpty() {
        return !isEmpty();
    }

    public boolean isEmpty() {
        return this == EMPTY || !origin.isValid() || (actorId == 0 && UriUtils.nonRealOid(oid)
                && TextUtils.isEmpty(webFingerId) && TextUtils.isEmpty(actorName));
    }

    public boolean isPartiallyDefined() {
        return !origin.isValid() || UriUtils.nonRealOid(oid) || TextUtils.isEmpty(webFingerId)
                || TextUtils.isEmpty(actorName);
    }

    public boolean isIdentified() {
        return actorId != 0 && isOidReal();
    }

    public boolean isOidReal() {
        return UriUtils.isRealOid(oid);
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "Actor:EMPTY";
        }
        String str = Actor.class.getSimpleName();
        String members = "oid=" + oid + "; origin=" + origin.getName();
        if (actorId != 0) {
            members += "; id=" + actorId;
        }
        if (!TextUtils.isEmpty(actorName)) {
            members += "; username=" + actorName;
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

    public String getActorName() {
        return actorName;
    }

    public Actor setActorName(String actorName) {
        if (this == EMPTY) {
            throw new IllegalStateException("Cannot set actorName of EMPTY Actor");
        }
        this.actorName = SharedPreferencesUtil.isEmpty(actorName) ? "" : actorName.trim();
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

        Actor that = (Actor) o;
        if (!origin.equals(that.origin)) return false;
        if (actorId != 0 || that.actorId != 0) {
            return actorId == that.actorId;
        }
        if (UriUtils.isRealOid(oid) || UriUtils.isRealOid(that.oid)) {
            return oid.equals(that.oid);
        }
        if (!TextUtils.isEmpty(getWebFingerId()) || !TextUtils.isEmpty(that.getWebFingerId())) {
            return getWebFingerId().equals(that.getWebFingerId());
        }
        return getActorName().equals(that.getActorName());
    }

    @Override
    public int hashCode() {
        int result = origin.hashCode ();
        if (actorId != 0) {
            return 31 * result + (int) (actorId ^ (actorId >>> 32));
        }
        if (UriUtils.isRealOid(oid)) {
            return 31 * result + oid.hashCode();
        }
        if (!TextUtils.isEmpty(getWebFingerId())) {
            return 31 * result + getWebFingerId().hashCode();
        }
        return 31 * result + getActorName().hashCode();
    }

    /** Doesn't take origin into account */
    public boolean isSame(Actor that) {
        return  isSame(that, false);
    }

    public boolean isSame(Actor other, boolean sameOriginOnly) {
        if (this == other) return true;
        if (other == null) return false;
        if (actorId != 0) {
            if (actorId == other.actorId) return true;
        }
        if (origin.equals(other.origin)) {
            if (UriUtils.isRealOid(oid) && oid.equals(other.oid)) {
                return true;
            }
        } else if (sameOriginOnly) {
            return false;
        }
        return isWebFingerIdValid && other.isWebFingerIdValid && webFingerId.equals(other.webFingerId);
    }

    private void fixWebFingerId() {
        if (TextUtils.isEmpty(actorName)) return;
        if (actorName.contains("@")) {
            setWebFingerId(actorName);
        } else if (!UriUtils.isEmpty(profileUri)){
            if(origin.isValid()) {
                setWebFingerId(actorName + "@" + origin.fixUriforPermalink(profileUri).getHost());
            } else {
                setWebFingerId(actorName + "@" + profileUri.getHost());
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
            name = getActorName();
        }
        if (TextUtils.isEmpty(name)) {
            name = realName;
        }
        if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(oid)) {
            name = "oid: " + oid;
        }
        if (TextUtils.isEmpty(name)) {
            name = "id: " + actorId;
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
     * @return actorId
     */
    public long lookupActorId() {
        if (actorId == 0) {
            if (isOidReal()) {
                actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, origin.getId(), oid);
            }
        }
        if (actorId == 0 && isWebFingerIdValid()) {
            actorId = MyQuery.webFingerIdToId(origin.getId(), webFingerId);
        }
        if (actorId == 0 && !isWebFingerIdValid() && !TextUtils.isEmpty(actorName)) {
            actorId = MyQuery.actorNameToId(origin.getId(), actorName);
        }
        if (actorId == 0) {
            actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, origin.getId(), getTempOid());
        }
        if (actorId == 0 && hasAltTempOid()) {
            actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, origin.getId(), getAltTempOid());
        }
        return actorId;
    }

    public boolean hasAltTempOid() {
        return !getTempOid().equals(getAltTempOid()) && !TextUtils.isEmpty(actorName);
    }

    public boolean hasLatestMessage() {
        return latestActivity != null && !latestActivity.isEmpty() ;
    }

    public String getTempOid() {
        return getTempOid(webFingerId, actorName);
    }

    public String getAltTempOid() {
        return getTempOid("", actorName);
    }

    public static String getTempOid(String webFingerId, String validUserName) {
        String oid = isWebFingerIdValid(webFingerId) ? webFingerId : validUserName;
        return UriUtils.TEMP_OID_PREFIX + oid;
    }

    public List<Actor> extractActorsFromBodyText(String textIn, boolean replyOnly) {
        final String SEPARATORS = ", ;'=`~!#$%^&*(){}[]/";
        List<Actor> actors = new ArrayList<>();
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
            if (StringUtils.nonEmpty(validWebFingerId) || StringUtils.nonEmpty(validUserName)) {
                addExtractedUser(actors, validWebFingerId, validUserName);
            }
        }
        return actors;
    }

    private void addExtractedUser(List<Actor> actors, String webFingerId, String validUserName) {
        Actor actor = Actor.fromOriginAndActorOid(origin, "");
        if (Actor.isWebFingerIdValid(webFingerId)) {
            actor.setWebFingerId(webFingerId);
        } else {
            // Try a host of the Author, next - a host of this Social network
            for (String host : Arrays.asList(getHost(), origin.getHost())) {
                if (UrlUtils.hostIsValid(host)) {
                    final String possibleWebFingerId = validUserName + "@" + host;
                    actor.actorId = MyQuery.webFingerIdToId(origin.getId(), possibleWebFingerId);
                    if (actor.actorId != 0) {
                        actor.setWebFingerId(possibleWebFingerId);
                        break;
                    }
                }
            }
        }
        actor.setActorName(validUserName);
        actor.lookupActorId();
        if (!actors.contains(actor)) {
            actors.add(actor);
        }
    }

    public String getHost() {
        int pos = getWebFingerId().indexOf('@');
        if (pos >= 0) {
            return getWebFingerId().substring(pos + 1);
        }
        return StringUtils.nonEmpty(profileUri.getHost()) ? profileUri.getHost() : "";
    }
    
    public String getDescription() {
        return description;
    }

    public Actor setDescription(String description) {
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
    public int compareTo(Actor another) {
        if (actorId != 0 && another.actorId != 0) {
            if (actorId == another.actorId) {
                return 0;
            }
            return origin.getId() > another.origin.getId() ? 1 : -1;
        }
        if (origin.getId() != another.origin.getId()) {
            return origin.getId() > another.origin.getId() ? 1 : -1;
        }
        return oid.compareTo(another.oid);
    }

    public AActivity getLatestActivity() {
        return latestActivity;
    }

    public void setLatestActivity(@NonNull AActivity latestActivity) {
        this.latestActivity = latestActivity;
        if (this.latestActivity.getAuthor().isEmpty()) {
            this.latestActivity.setAuthor(this);
        }
    }

    public String toUserTitle(boolean showWebFingerId) {
        StringBuilder builder = new StringBuilder();
        if (showWebFingerId && !TextUtils.isEmpty(getWebFingerId())) {
            builder.append(getWebFingerId());
        } else if (!TextUtils.isEmpty(getActorName())) {
            builder.append("@" + getActorName());
        }
        if (!TextUtils.isEmpty(getRealName())) {
            I18n.appendWithSpace(builder, "(" + getRealName() + ")");
        }
        return builder.toString();
    }

    public String getTimelineActorName() {
        return MyQuery.actorIdToWebfingerId(actorId);
    }
}
