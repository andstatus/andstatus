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

import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.user.User;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author yvolk@yurivolkov.com
 */
public class Actor implements Comparable<Actor> {
    public static final Actor EMPTY = new Actor(Origin.EMPTY, "");
    // RegEx from http://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    public static final String WEBFINGER_ID_REGEX = "^[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$";
    @NonNull
    public final String oid;
    private String username = "";

    private String webFingerId = "";
    private boolean isWebFingerIdValid = false;

    private String realName = "";
    private String description = "";
    public String location = "";

    private Uri profileUri = Uri.EMPTY;
    private String homepage = "";
    public String avatarUrl = "";
    public String bannerUrl = "";

    public long notesCount = 0;
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

    public User user = User.EMPTY;

    @NonNull
    public static Actor fromOriginAndActorOid(@NonNull Origin origin, String actorOid) {
        return new Actor(origin, actorOid);
    }

    public static Actor load(@NonNull MyContext myContext, long actorId, Supplier<Actor> supplier) {
        if (actorId == 0) return Actor.EMPTY;
        Actor actor1 = myContext.users().actors.getOrDefault(actorId, Actor.EMPTY);
        return actor1.nonEmpty() ? actor1 : loadInternal(myContext, actorId, supplier);
    }

    private static Actor loadInternal(@NonNull MyContext myContext, long actorId, Supplier<Actor> supplier) {
        final String sql = "SELECT " + Actor.getActorAndUserSqlColumns()
                + " FROM " + Actor.getActorAndUserSqlTables()
                + " WHERE " + ActorTable.TABLE_NAME + "." + ActorTable._ID + "=" + actorId;
        final Function<Cursor, Actor> function = cursor -> fromCursor(myContext, cursor);
        return MyQuery.get(myContext, sql, function).stream().findFirst().orElseGet(supplier);
    }

    @NonNull
    public static String getActorAndUserSqlTables() {
        return ActorTable.TABLE_NAME
                + " INNER JOIN " + UserTable.TABLE_NAME
                + " ON " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + "=" + UserTable.TABLE_NAME + "." + UserTable._ID;
    }

    @NonNull
    public static String getActorAndUserSqlColumns() {
        return ActorTable.TABLE_NAME + "." + ActorTable._ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_OID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.USERNAME
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.WEBFINGER_ID
                + ", " + UserTable.TABLE_NAME + "." + UserTable.IS_MY
                + ", " + UserTable.TABLE_NAME + "." + UserTable.KNOWN_AS;
    }

    @NonNull
    public static Actor fromCursor(MyContext myContext, Cursor cursor) {
        final long actorId = DbUtils.getLong(cursor, ActorTable._ID);
        Actor actor = myContext.users().actors.getOrDefault(actorId, Actor.EMPTY);
        if (actor.isEmpty()) {
            actor = Actor.fromOriginAndActorOid(
                    myContext.origins().fromId(DbUtils.getLong(cursor, ActorTable.ORIGIN_ID)),
                    DbUtils.getString(cursor, ActorTable.ACTOR_OID));
            actor.actorId = actorId;
            actor.setUsername(DbUtils.getString(cursor, ActorTable.USERNAME));
            actor.setWebFingerId(DbUtils.getString(cursor, ActorTable.WEBFINGER_ID));
            actor.user = User.fromCursor(myContext, cursor);
            myContext.users().addIfAbsent(actor);
        }
        return actor;
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
                && TextUtils.isEmpty(webFingerId) && TextUtils.isEmpty(username));
    }

    public boolean isPartiallyDefined() {
        return !origin.isValid() || UriUtils.nonRealOid(oid) || TextUtils.isEmpty(webFingerId)
                || TextUtils.isEmpty(username);
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
        String members = "origin=" + origin.getName() + ",";
        if (actorId != 0) {
            members += "id=" + actorId + ",";
        }
        if (!TextUtils.isEmpty(oid)) {
            members += "oid=" + oid + ",";
        }
        if (isWebFingerIdValid()) {
            members += getWebFingerId() + ",";
        }
        if (!TextUtils.isEmpty(username)) {
            members += "username=" + username + ",";
        }
        if (!TextUtils.isEmpty(realName)) {
            members += "realName=" + realName + ",";
        }
        if (user.nonEmpty()) {
            members += user + ",";
        }
        if (!Uri.EMPTY.equals(profileUri)) {
            members += "profileUri=" + profileUri.toString() + ",";
        }
        if (hasLatestNote()) {
            members += "latest note present,";
        }
        return MyLog.formatKeyValue(this, members);
    }

    public String getUsername() {
        return username;
    }

    public Actor setUsername(String username) {
        if (this == EMPTY) {
            throw new IllegalStateException("Cannot set username of EMPTY Actor");
        }
        this.username = SharedPreferencesUtil.isEmpty(username) ? "" : username.trim();
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
        return getUsername().equals(that.getUsername());
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
        return 31 * result + getUsername().hashCode();
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
        if (TextUtils.isEmpty(username)) return;
        if (username.contains("@")) {
            setWebFingerId(username);
        } else if (!UriUtils.isEmpty(profileUri)){
            if(origin.isValid()) {
                setWebFingerId(username + "@" + origin.fixUriforPermalink(profileUri).getHost());
            } else {
                setWebFingerId(username + "@" + profileUri.getHost());
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
            name = getUsername();
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

    /** Lookup the application's id from other IDs */
    public void lookupActorId(@NonNull MyContext myContext) {
        if (actorId == 0 && isOidReal()) {
            actorId = MyQuery.oidToId(myContext, OidEnum.ACTOR_OID, origin.getId(), oid);
        }
        if (actorId == 0 && isWebFingerIdValid()) {
            actorId = MyQuery.webFingerIdToId(origin.getId(), webFingerId);
        }
        if (actorId == 0 && !isWebFingerIdValid() && !TextUtils.isEmpty(username)) {
            actorId = MyQuery.usernameToId(origin.getId(), username);
        }
        if (actorId == 0) {
            actorId = MyQuery.oidToId(myContext, OidEnum.ACTOR_OID, origin.getId(), getTempOid());
        }
        if (actorId == 0 && hasAltTempOid()) {
            actorId = MyQuery.oidToId(myContext, OidEnum.ACTOR_OID, origin.getId(), getAltTempOid());
        }
    }

    public boolean hasAltTempOid() {
        return !getTempOid().equals(getAltTempOid()) && !TextUtils.isEmpty(username);
    }

    public boolean hasLatestNote() {
        return latestActivity != null && !latestActivity.isEmpty() ;
    }

    public String getTempOid() {
        return getTempOid(webFingerId, username);
    }

    public String getAltTempOid() {
        return getTempOid("", username);
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
            String validUsername = "";
            String validWebFingerId = "";
            int ind=atPos+1;
            for (; ind < text.length(); ind++) {
                if (SEPARATORS.indexOf(text.charAt(ind)) >= 0) {
                    break;
                }
                String username = text.substring(atPos+1, ind + 1);
                if (origin.isUsernameValid(username)) {
                    validUsername = username;
                }
                if (isWebFingerIdValid(username)) {
                    validWebFingerId = username;
                }
            }
            if (ind < text.length()) {
                text = text.substring(ind);
            } else {
                text = "";
            }
            if (StringUtils.nonEmpty(validWebFingerId) || StringUtils.nonEmpty(validUsername)) {
                addExtractedActor(actors, validWebFingerId, validUsername);
            }
        }
        return actors;
    }

    private void addExtractedActor(List<Actor> actors, String webFingerId, String validUsername) {
        Actor actor = Actor.fromOriginAndActorOid(origin, "");
        if (Actor.isWebFingerIdValid(webFingerId)) {
            actor.setWebFingerId(webFingerId);
        } else {
            // Try a host of the Author, next - a host of this Social network
            for (String host : Arrays.asList(getHost(), origin.getHost())) {
                if (UrlUtils.hostIsValid(host)) {
                    final String possibleWebFingerId = validUsername + "@" + host;
                    actor.actorId = MyQuery.webFingerIdToId(origin.getId(), possibleWebFingerId);
                    if (actor.actorId != 0) {
                        actor.setWebFingerId(possibleWebFingerId);
                        break;
                    }
                }
            }
        }
        actor.setUsername(validUsername);
        actor.lookupActorId(MyContextHolder.get());
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

    public String toActorTitle(boolean showWebFingerId) {
        StringBuilder builder = new StringBuilder();
        if (showWebFingerId && !TextUtils.isEmpty(getWebFingerId())) {
            builder.append(getWebFingerId());
        } else if (!TextUtils.isEmpty(getUsername())) {
            builder.append("@" + getUsername());
        }
        if (!TextUtils.isEmpty(getRealName())) {
            I18n.appendWithSpace(builder, "(" + getRealName() + ")");
        }
        return builder.toString();
    }

    public String getTimelineUsername() {
        return MyQuery.actorIdToWebfingerId(actorId);
    }

    public Actor lookupUser(MyContext myContext) {
        if (user == User.EMPTY && actorId != 0) {
            user = User.load(myContext, actorId);
        }
        if (user == User.EMPTY && isWebFingerIdValid()) {
            user = User.load(myContext, MyQuery.webFingerIdToId(0, webFingerId));
        }
        if (user == User.EMPTY) {
            user = User.getNew();
        }
        return this;
    }

    public void saveUser(MyContext myContext) {
        if (user.isMyUser().unknown && myContext.users().containsMe(this)) {
            user.setIsMyUser(TriState.TRUE);
        }
        if (user.userId == 0) user.setKnownAs(getNamePreferablyWebFingerId());
        user.save(myContext);
    }
}
