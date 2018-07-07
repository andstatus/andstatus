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
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.data.ProjectionMap;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.user.User;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

/**
 * @author yvolk@yurivolkov.com
 */
public class Actor implements Comparable<Actor>, IsEmpty {
    public static final Actor EMPTY = new Actor(Origin.EMPTY, "").setUsername("Empty");
    public static final Actor PUBLIC = new Actor(Origin.EMPTY, "https://www.w3.org/ns/activitystreams#Public").setUsername("Public");

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
    public AvatarFile avatarFile = AvatarFile.EMPTY;

    public User user = User.EMPTY;

    @NonNull
    public static Actor fromOriginAndActorOid(@NonNull Origin origin, String actorOid) {
        return new Actor(origin, actorOid);
    }

    @NonNull
    public static Actor load(@NonNull MyContext myContext, long actorId) {
        return load(myContext, actorId, () -> Actor.EMPTY);
    }

    public static Actor load(@NonNull MyContext myContext, long actorId, Supplier<Actor> supplier) {
        if (actorId == 0) return supplier.get();

        Actor actor1 = myContext.users().actors.getOrDefault(actorId, Actor.EMPTY);
        return actor1.nonEmpty()
                ? actor1
                : loadInternal(myContext, actorId, supplier);
    }

    private static Actor loadInternal(@NonNull MyContext myContext, long actorId, Supplier<Actor> supplier) {
        final String sql = "SELECT " + Actor.getActorAndUserSqlColumns(false)
                + " FROM " + Actor.getActorAndUserSqlTables()
                + " WHERE " + ActorTable.TABLE_NAME + "." + ActorTable._ID + "=" + actorId;
        final Function<Cursor, Actor> function = cursor -> fromCursor(myContext, cursor);
        Actor actor = MyQuery.get(myContext, sql, function).stream().findFirst().orElseGet(supplier);
        return actor;
    }

    @NonNull
    public static String getActorAndUserSqlTables() {
        return getActorAndUserSqlTables(false, false);
    }

    @NonNull
    public static String getActorAndUserSqlTables(boolean optionalUser, boolean withAvatar) {
        final String tables = ActorTable.TABLE_NAME + " "
                + (optionalUser ? "LEFT" : "INNER") + " JOIN " + UserTable.TABLE_NAME
                + " ON " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + "=" + UserTable.TABLE_NAME + "." + UserTable._ID;
        return withAvatar ? addAvatarImageTable(tables) : tables;
    }

    @NonNull
    private static String addAvatarImageTable(String tables) {
        return "(" + tables + ") LEFT OUTER JOIN (SELECT "
                + DownloadTable.ACTOR_ID + ", "
                + DownloadTable.DOWNLOAD_STATUS + ", "
                + DownloadTable.FILE_NAME
                + " FROM " + DownloadTable.TABLE_NAME + ") AS " + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS
                + " ON "
                + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.DOWNLOAD_STATUS
                + " IN (" + DownloadStatus.LOADED.save() + ", " + DownloadStatus.ABSENT.save() + ") AND "
                + ProjectionMap.AVATAR_IMAGE_TABLE_ALIAS + "." + DownloadTable.ACTOR_ID
                + "=" + ActorTable.TABLE_NAME + "." + BaseColumns._ID;
    }

    @NonNull
    public static String getActorAndUserSqlColumns(boolean withAvatar) {
        return ActorTable.TABLE_NAME + "." + ActorTable._ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.USER_ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.ORIGIN_ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.ACTOR_OID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.REAL_NAME
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.USERNAME
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.WEBFINGER_ID
                + ", " + ActorTable.TABLE_NAME + "." + ActorTable.UPDATED_DATE
                + ", " + UserTable.TABLE_NAME + "." + UserTable.IS_MY
                + ", " + UserTable.TABLE_NAME + "." + UserTable.KNOWN_AS
                + (withAvatar ? ", " + ProjectionMap.ACTORLIST.get(DownloadTable.AVATAR_FILE_NAME) : "");
    }

    @NonNull
    public static Actor fromCursor(MyContext myContext, Cursor cursor) {
        final long actorId = DbUtils.getLong(cursor, ActorTable._ID);
        Actor actor = myContext.users().actors.getOrDefault(actorId, Actor.EMPTY);
        if (actor.isEmpty()) {
            actor = Actor.fromOriginAndActorId(
                    myContext.origins().fromId(DbUtils.getLong(cursor, ActorTable.ORIGIN_ID)),
                    actorId,
                    DbUtils.getString(cursor, ActorTable.ACTOR_OID));
            actor.setRealName(DbUtils.getString(cursor, ActorTable.REAL_NAME));
            actor.setUsername(DbUtils.getString(cursor, ActorTable.USERNAME));
            actor.setWebFingerId(DbUtils.getString(cursor, ActorTable.WEBFINGER_ID));
            actor.setUpdatedDate(DbUtils.getLong(cursor, ActorTable.UPDATED_DATE));
            actor.user = User.fromCursor(myContext, cursor);
            actor.avatarFile = AvatarFile.fromCursor(actorId, cursor);
            myContext.users().updateCache(actor);
        }
        return actor;
    }

    public static Actor fromOriginAndActorId(@NonNull Origin origin, long actorId) {
        return fromOriginAndActorId(origin, actorId, "");
    }

    public static Actor fromOriginAndActorId(@NonNull Origin origin, long actorId, String actorOid) {
        Actor actor = new Actor(origin, actorOid);
        actor.actorId = actorId;
        return actor;
    }

    private Actor(@NonNull Origin origin, String actorOid) {
        this.origin = origin;
        this.oid = StringUtils.isEmpty(actorOid) ? "" : actorOid;
    }

    /** this Actor is MyAccount and the Actor updates objActor */
    @NonNull
    public AActivity update(Actor objActor) {
        return update(this, objActor);
    }

    /** this actor updates objActor */
    @NonNull
    public AActivity update(Actor accountActor, @NonNull Actor objActor) {
        return objActor == EMPTY
                ? AActivity.EMPTY
                : act(accountActor, ActivityType.UPDATE, objActor);
    }

    /** this actor acts on objActor */
    @NonNull
    public AActivity act(Actor accountActor, @NonNull ActivityType activityType, @NonNull Actor objActor) {
        if (this == EMPTY || accountActor == EMPTY || objActor == EMPTY) {
            return AActivity.EMPTY;
        }
        AActivity mbActivity = AActivity.from(accountActor, activityType);
        mbActivity.setActor(this);
        mbActivity.setObjActor(objActor);
        return mbActivity;
    }

    @Override
    public boolean isEmpty() {
        return this == EMPTY || !origin.isValid() || (actorId == 0 && UriUtils.nonRealOid(oid)
                && StringUtils.isEmpty(webFingerId) && !origin.isUsernameValid(username));
    }

    public boolean isPartiallyDefined() {
        return !origin.isValid() || UriUtils.nonRealOid(oid) || StringUtils.isEmpty(webFingerId)
                || !origin.isUsernameValid(username);
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
        if (!StringUtils.isEmpty(oid)) {
            members += "oid=" + oid + ",";
        }
        if (isWebFingerIdValid()) {
            members += getWebFingerId() + ",";
        }
        if (!StringUtils.isEmpty(username)) {
            members += "username=" + username + ",";
        }
        if (!StringUtils.isEmpty(realName)) {
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

    public Actor setProfileUrl(String url) {
        this.profileUri = UriUtils.fromString(url);
        fixWebFingerId();
        return this;
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
        if (!StringUtils.isEmpty(getWebFingerId()) || !StringUtils.isEmpty(that.getWebFingerId())) {
            return getWebFingerId().equals(that.getWebFingerId());
        }
        return getUsername().equals(that.getUsername());
    }

    @Override
    public int hashCode() {
        int result = origin.hashCode ();
        if (actorId != 0) {
            return 31 * result + Long.hashCode(actorId);
        }
        if (UriUtils.isRealOid(oid)) {
            return 31 * result + oid.hashCode();
        }
        if (!StringUtils.isEmpty(getWebFingerId())) {
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

    public boolean notSameUser(@NonNull Actor other) {
        return !isSameUser(other);
    }

    public boolean isSameUser(@NonNull Actor other) {
        return user.actorIds.isEmpty() || other.actorId == 0
                ? (other.user.actorIds.isEmpty() || actorId == 0
                    ? isSame(other)
                    : other.user.actorIds.contains(actorId))
                : user.actorIds.contains(other.actorId);
    }

    private void fixWebFingerId() {
        if (StringUtils.isEmpty(username)) return;
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
            this.webFingerId = webFingerId.toLowerCase();
            isWebFingerIdValid = true;
        }
    }

    public String getWebFingerId() {
        return webFingerId;
    }

    public String getNamePreferablyWebFingerId() {
        if (isWebFingerIdValid) return webFingerId;
        if (StringUtils.nonEmpty(username)) return username;
        if (StringUtils.nonEmpty(realName)) return realName;
        if (StringUtils.nonEmpty(oid)) return "oid:" + oid;
        return "id:" + actorId;
    }

    public boolean isWebFingerIdValid() {
        return  isWebFingerIdValid;
    }

    static boolean isWebFingerIdValid(String webFingerId) {
        return StringUtils.nonEmpty(webFingerId) && Patterns.WEBFINGER_ID_REGEX_PATTERN.matcher(webFingerId).matches();
    }

    /** Lookup the application's id from other IDs */
    public void lookupActorId(@NonNull MyContext myContext) {
        if (actorId == 0 && isOidReal()) {
            actorId = MyQuery.oidToId(myContext, OidEnum.ACTOR_OID, origin.getId(), oid);
        }
        if (actorId == 0 && isWebFingerIdValid()) {
            actorId = MyQuery.webFingerIdToId(origin.getId(), webFingerId);
        }
        if (actorId == 0 && !isWebFingerIdValid() && !StringUtils.isEmpty(username)) {
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
        return !getTempOid().equals(getAltTempOid()) && !StringUtils.isEmpty(username);
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

    public List<Actor> extractActorsFromContent(String textIn, boolean replyOnly, Actor inReplyToActor) {
        final String SEPARATORS = ", ;'=`~!#$%^&*(){}[]/";
        List<Actor> actors = new ArrayList<>();
        String text = MyHtml.fromHtml(textIn);
        while (!StringUtils.isEmpty(text)) {
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
                addExtractedActor(actors, validWebFingerId, validUsername, inReplyToActor);
            }
        }
        return actors;
    }

    private void addExtractedActor(List<Actor> actors, String webFingerId, String validUsername, Actor inReplyToActor) {
        Actor actor = Actor.fromOriginAndActorOid(origin, "");
        if (Actor.isWebFingerIdValid(webFingerId)) {
            actor.setWebFingerId(webFingerId);
            actor.setUsername(validUsername);
        } else {
            // Is this a reply to Actor?
            if (validUsername.equalsIgnoreCase(inReplyToActor.getUsername())) {
                actor = inReplyToActor;
            } else if (validUsername.equalsIgnoreCase(getUsername())) {
                actor = this;
            } else {
                // Try 1. a host of the Author, 2. A host of Replied to user, 3. A host of this Social network
                for (String host : new HashSet<>(Arrays.asList(getHost(), origin.getHost()))) {
                    if (UrlUtils.hostIsValid(host)) {
                        final String possibleWebFingerId = validUsername + "@" + host;
                        actor.actorId = MyQuery.webFingerIdToId(origin.getId(), possibleWebFingerId);
                        if (actor.actorId != 0) {
                            actor.setWebFingerId(possibleWebFingerId);
                            break;
                        }
                    }
                }
                actor.setUsername(validUsername);
            }
        }
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
        this.updatedDate = updatedDate < SOME_TIME_AGO ? SOME_TIME_AGO : updatedDate;
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
        if (showWebFingerId && !StringUtils.isEmpty(getWebFingerId())) {
            builder.append(getWebFingerId());
        } else if (!StringUtils.isEmpty(getUsername())) {
            builder.append("@" + getUsername());
        }
        if (!StringUtils.isEmpty(getRealName())) {
            MyStringBuilder.appendWithSpace(builder, "(" + getRealName() + ")");
        }
        return builder.toString();
    }

    public String getTimelineUsername() {
        String name1 = getTimelineUsername1();
        if (StringUtils.nonEmpty(name1)) return name1;
        return getNamePreferablyWebFingerId();
    }

    private String getTimelineUsername1() {
        switch (MyPreferences.getActorInTimeline()) {
            case AT_USERNAME:
                return StringUtils.isEmpty(username) ? "" : "@" + username;
            case WEBFINGER_ID:
                return isWebFingerIdValid ? webFingerId : "";
            case REAL_NAME:
                return realName;
            case REAL_NAME_AT_USERNAME:
                return StringUtils.nonEmpty(realName) && StringUtils.nonEmpty(username)
                    ? realName + " @" + username
                    : username;
            default:
                return username;
        }
    }

    public Actor lookupUser(MyContext myContext) {
        return myContext.users().lookupUser(this);
    }

    public void saveUser(MyContext myContext) {
        if (user.isMyUser().unknown && myContext.users().isMe(this)) {
            user.setIsMyUser(TriState.TRUE);
        }
        if (user.userId == 0) user.setKnownAs(getNamePreferablyWebFingerId());
        user.save(myContext);
    }

    public boolean hasAvatar() {
        return StringUtils.nonEmpty(avatarUrl);
    }

    public void loadFromInternet() {
        MyLog.v(this, () -> "Actor " + this + " will be loaded from the Internet");
        MyServiceManager.sendForegroundCommand(
                CommandData.newActorCommand(CommandEnum.GET_ACTOR, actorId, getUsername()));
    }

   public boolean isPublic() {
        return PUBLIC.equals(this);
    }

    public boolean nonPublic() {
        return !isPublic();
    }
}
