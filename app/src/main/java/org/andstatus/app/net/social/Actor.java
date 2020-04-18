/*
 * Copyright (C) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.actor.Group;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.origin.ActorReference;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.user.User;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.LazyVal;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.NullUtil;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.vavr.control.Try;

import static org.andstatus.app.net.social.Patterns.WEBFINGER_ID_CHARS;
import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;
import static org.junit.Assert.fail;

/**
 * @author yvolk@yurivolkov.com
 */
public class Actor implements Comparable<Actor>, IsEmpty {
    public static final Actor EMPTY = newUnknown(Origin.EMPTY, GroupType.UNKNOWN).setUsername("Empty");
    public static final Try<Actor> TRY_EMPTY = Try.success(EMPTY);
    public static final Actor PUBLIC = fromTwoIds(Origin.EMPTY, GroupType.PUBLIC, 0,
        "https://www.w3.org/ns/activitystreams#Public").setUsername("Public");
    public static final Actor FOLLOWERS = fromTwoIds(Origin.EMPTY, GroupType.FOLLOWERS, 0,
            "org.andstatus.app.net.social.Actor#Followers").setUsername("Followers");

    @NonNull
    public final String oid;
    public final GroupType groupType;
    private long parentActorId = 0;
    private LazyVal<Actor> parentActor = LazyVal.of(() -> EMPTY);

    private String username = "";

    private String webFingerId = "";
    private boolean isWebFingerIdValid = false;

    private String realName = "";
    private String summary = "";
    public String location = "";

    private Uri profileUri = Uri.EMPTY;
    private String homepage = "";
    private Uri avatarUri = Uri.EMPTY;
    public final ActorEndpoints endpoints;
    private final LazyVal<String> connectionHost = LazyVal.of(this::evalConnectionHost);
    private final LazyVal<String> idHost = LazyVal.of(this::evalIdHost);

    public long notesCount = 0;
    public long favoritesCount = 0;
    public long followingCount = 0;
    public long followersCount = 0;

    private long createdDate = DATETIME_MILLIS_NEVER;
    private long updatedDate = DATETIME_MILLIS_NEVER;

    private AActivity latestActivity = null;

    // Hack for Twitter-like origins...
    public TriState isMyFriend = TriState.UNKNOWN;

    // In our system
    @NonNull
    public final Origin origin;
    public volatile long actorId = 0L;
    public AvatarFile avatarFile = AvatarFile.EMPTY;

    public volatile User user = User.EMPTY;

    private volatile TriState isFullyDefined = TriState.UNKNOWN;

    @NonNull
    public static Actor getEmpty() {
        return EMPTY;
    }

    @NonNull
    public static Actor load(@NonNull MyContext myContext, long actorId) {
        return load(myContext, actorId, false, Actor::getEmpty);
    }

    public static Actor load(@NonNull MyContext myContext, long actorId, boolean reloadFirst, Supplier<Actor> supplier) {
        if (actorId == 0) return supplier.get();

        Actor cached = myContext.users().actors.getOrDefault(actorId, Actor.EMPTY);
        return MyAsyncTask.nonUiThread() && (reloadFirst || cached.isNotFullyDefined())
                ? loadFromDatabase(myContext, actorId, supplier, true).betterToCache(cached)
                : cached;
    }

    public List<TimelineType> getDefaultMyAccountTimelineTypes() {
        return origin.getOriginType().isPrivatePostsSupported()
                ? TimelineType.getDefaultMyAccountTimelineTypes()
                : TimelineType.getDefaultMyAccountTimelineTypes().stream()
                    .filter(t -> t != TimelineType.PRIVATE)
                    .collect(Collectors.toList());
    }

    private Actor betterToCache(Actor other) {
        return isBetterToCacheThan(other) ?  this : other;
    }

    public static Actor loadFromDatabase(@NonNull MyContext myContext, long actorId, Supplier<Actor> supplier,
                                         boolean useCache) {
        final String sql = "SELECT " + ActorSql.selectFullProjection()
                + " FROM " + ActorSql.allTables()
                + " WHERE " + ActorTable.TABLE_NAME + "." + ActorTable._ID + "=" + actorId;
        final Function<Cursor, Actor> function = cursor -> fromCursor(myContext, cursor, useCache);
        return MyQuery.get(myContext, sql, function).stream().findFirst().orElseGet(supplier);
    }

    /** Updates cache on load */
    @NonNull
    public static Actor fromCursor(MyContext myContext, Cursor cursor, boolean useCache) {
        final long updatedDate = DbUtils.getLong(cursor, ActorTable.UPDATED_DATE);
        Actor actor = Actor.fromTwoIds(
                myContext.origins().fromId(DbUtils.getLong(cursor, ActorTable.ORIGIN_ID)),
                GroupType.fromId(DbUtils.getLong(cursor, ActorTable.GROUP_TYPE)),
                DbUtils.getLong(cursor, ActorTable.ACTOR_ID),
                DbUtils.getString(cursor, ActorTable.ACTOR_OID));

        actor.setParentActorId(myContext, DbUtils.getLong(cursor, ActorTable.PARENT_ACTOR_ID));

        actor.setRealName(DbUtils.getString(cursor, ActorTable.REAL_NAME));
        actor.setUsername(DbUtils.getString(cursor, ActorTable.USERNAME));
        actor.setWebFingerId(DbUtils.getString(cursor, ActorTable.WEBFINGER_ID));

        actor.setSummary(DbUtils.getString(cursor, ActorTable.SUMMARY));
        actor.location = DbUtils.getString(cursor, ActorTable.LOCATION);

        actor.setProfileUrl(DbUtils.getString(cursor, ActorTable.PROFILE_PAGE));
        actor.setHomepage(DbUtils.getString(cursor, ActorTable.HOMEPAGE));
        actor.setAvatarUrl(DbUtils.getString(cursor, ActorTable.AVATAR_URL));

        actor.notesCount = DbUtils.getLong(cursor, ActorTable.NOTES_COUNT);
        actor.favoritesCount = DbUtils.getLong(cursor, ActorTable.FAVORITES_COUNT);
        actor.followingCount = DbUtils.getLong(cursor, ActorTable.FOLLOWING_COUNT);
        actor.followersCount = DbUtils.getLong(cursor, ActorTable.FOLLOWERS_COUNT);

        actor.setCreatedDate(DbUtils.getLong(cursor, ActorTable.CREATED_DATE));
        actor.setUpdatedDate(updatedDate);

        actor.user = User.fromCursor(myContext, cursor, useCache);
        actor.avatarFile = AvatarFile.fromCursor(actor, cursor);
        if (useCache) {
            Actor cachedActor = myContext.users().actors.getOrDefault(actor.actorId, Actor.EMPTY);
            if (actor.isBetterToCacheThan(cachedActor)) {
                myContext.users().updateCache(actor);
                return actor;
            }
            return cachedActor;
        } else {
            return actor;
        }
    }

    public static Actor newUnknown(@NonNull Origin origin, GroupType groupType) {
        return fromTwoIds(origin, groupType, 0, "");
    }

    public static Actor fromId(@NonNull Origin origin, long actorId) {
        return fromTwoIds(origin, GroupType.UNKNOWN, actorId, "");
    }

    public static Actor fromOid(@NonNull Origin origin, String actorOid) {
        return fromTwoIds(origin, GroupType.UNKNOWN, 0, actorOid);
    }

    public static Actor fromTwoIds(@NonNull Origin origin, GroupType groupType, long actorId, String actorOid) {
        return new Actor(origin, groupType, actorId, actorOid);
    }

    private Actor(@NonNull Origin origin, GroupType groupType, long actorId, String actorOid) {
        this.origin = origin;
        this.groupType = groupType;
        this.actorId = actorId;
        this.oid = StringUtil.isEmpty(actorOid) ? "" : actorOid;
        endpoints = ActorEndpoints.from(origin.myContext, actorId);
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

    public boolean isConstant() {
        return this == EMPTY || this == PUBLIC || this == FOLLOWERS;
    }

    @Override
    public boolean isEmpty() {
        if (this == EMPTY) return true;
        if (isConstant()) return false;
        return !origin.isValid() ||
                (actorId == 0 && UriUtils.nonRealOid(oid) && !isWebFingerIdValid() && !isUsernameValid());
    }

    public boolean dontStore() {
        return isConstant();
    }

    public boolean isFullyDefined() {
        if (isFullyDefined.unknown) {
            isFullyDefined = calcIsFullyDefined();
        }
        return isFullyDefined.isTrue;
    }

    private TriState calcIsFullyDefined() {
        if (isEmpty() || UriUtils.nonRealOid(oid)) return TriState.FALSE;
        if (groupType.isGroupLike) return TriState.TRUE;
        return TriState.fromBoolean(isWebFingerIdValid() && isUsernameValid());
    }

    public boolean isNotFullyDefined() {
        return !isFullyDefined();
    }

    public boolean isBetterToCacheThan(Actor other) {
        if (this == other) return false;

        if (other == null || other == EMPTY ||
                (isFullyDefined() && other.isNotFullyDefined())) return true;
        if (this == EMPTY || (isNotFullyDefined() && other.isFullyDefined())) return false;

        if (isFullyDefined()) {
            if (getUpdatedDate() != other.getUpdatedDate()) {
                return getUpdatedDate() > other.getUpdatedDate();
            }
            if (avatarFile.downloadedDate != other.avatarFile.downloadedDate) {
                return avatarFile.downloadedDate > other.avatarFile.downloadedDate;
            }
            return notesCount > other.notesCount;
        } else {
            if (isOidReal() && !other.isOidReal()) return true;
            if (!isOidReal() && other.isOidReal()) return false;

            if (isUsernameValid() && !other.isUsernameValid()) return true;
            if (!isUsernameValid() && other.isUsernameValid()) return false;

            if (isWebFingerIdValid() && !other.isWebFingerIdValid()) return true;
            if (!isWebFingerIdValid() && other.isWebFingerIdValid()) return false;

            if (UriUtils.nonEmpty(profileUri) && UriUtils.isEmpty(other.profileUri)) return true;
            if (UriUtils.isEmpty(profileUri) && UriUtils.nonEmpty(other.profileUri)) return false;

            return getUpdatedDate() > other.getUpdatedDate();
        }
    }

    public boolean isIdentified() {
        return actorId != 0 && isOidReal();
    }

    public boolean isOidReal() {
        return UriUtils.isRealOid(oid);
    }

    public boolean canGetActor() {
        return (isOidReal() || isWebFingerIdValid()) && StringUtil.nonEmpty(getConnectionHost());
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "Actor:EMPTY";
        }
        MyStringBuilder members = MyStringBuilder.of("origin:" + origin.getName())
        .withComma("id", actorId)
        .withComma("oid", oid)
        .withComma(isWebFingerIdValid() ? "webFingerId" : "", StringUtil.isEmpty(webFingerId)
                ? ""
                : isWebFingerIdValid() ? webFingerId : "(invalid webFingerId)")
        .withComma("username", username)
        .withComma("realName", realName)
        .withComma("groupType", groupType == GroupType.UNKNOWN ? "" : groupType)
        .withComma("", user, user::nonEmpty)
        .withComma("profileUri", profileUri, UriUtils::nonEmpty)
        .withComma("avatar", avatarUri, UriUtils::nonEmpty)
        .withComma("avatarFile", avatarFile, AvatarFile::nonEmpty)
        .withComma("banner", endpoints.findFirst(ActorEndpointType.BANNER).orElse(null))
        .withComma("", "latest note present", this::hasLatestNote);
        if (parentActor.isEvaluated() && parentActor.get().nonEmpty()) {
            members.withComma("parent", parentActor.get());
        } else if (parentActorId != 0) {
            members.withComma("parentId", parentActorId);
        }
        return MyStringBuilder.formatKeyValue(this, members);
    }

    public String getUsername() {
        return username;
    }

    public String getUniqueNameWithOrigin() {
        return getUniqueName() + AccountName.ORIGIN_SEPARATOR + origin.getName();
    }

    public String getUniqueName() {
        if (StringUtil.nonEmptyNonTemp(username)) return username + getOptAtHostForUniqueName();
        if (StringUtil.nonEmptyNonTemp(realName)) return realName + getOptAtHostForUniqueName();
        if (isWebFingerIdValid()) return webFingerId;
        if (StringUtil.nonEmptyNonTemp(oid)) return oid;
        return "id:" + actorId + getOptAtHostForUniqueName();
    }

    private String getOptAtHostForUniqueName() {
        return origin.getOriginType().uniqueNameHasHost()
                ? (StringUtil.isEmpty(getIdHost()) ? "" : "@" + getIdHost())
                : "";
    }

    public Actor withUniqueName(String uniqueName) {
        uniqueNameToUsername(origin, uniqueName).ifPresent(this::setUsername);
        uniqueNameToWebFingerId(origin, uniqueName).ifPresent(this::setWebFingerId);
        return this;
    }

    public static Optional<String> uniqueNameToUsername(Origin origin, String uniqueName) {
        if (StringUtil.nonEmpty(uniqueName)) {
            if (uniqueName.contains("@")) {
                final String nameBeforeTheLastAt = uniqueName.substring(0, uniqueName.lastIndexOf("@"));
                if (isWebFingerIdValid(uniqueName)) {
                    if (origin.isUsernameValid(nameBeforeTheLastAt)) return Optional.of(nameBeforeTheLastAt);
                } else {
                    int lastButOneIndex = nameBeforeTheLastAt.lastIndexOf("@");
                    if (lastButOneIndex > -1) {
                        // A case when a Username contains "@"
                        String potentialWebFingerId = uniqueName.substring(lastButOneIndex+1);
                        if (isWebFingerIdValid(potentialWebFingerId)) {
                            final String nameBeforeLastButOneAt = uniqueName.substring(0, lastButOneIndex);
                            if (origin.isUsernameValid(nameBeforeLastButOneAt)) return Optional.of(nameBeforeLastButOneAt);
                        }
                    }
                }
            }
            if (origin.isUsernameValid(uniqueName)) return Optional.of(uniqueName);
        }
        return Optional.empty();
    }

    public static Optional<String> uniqueNameToWebFingerId(Origin origin, String uniqueName) {
        if (StringUtil.nonEmpty(uniqueName)) {
            if (uniqueName.contains("@")) {
                final String nameBeforeTheLastAt = uniqueName.substring(0, uniqueName.lastIndexOf("@"));
                if (isWebFingerIdValid(uniqueName)) {
                    return Optional.of(uniqueName.toLowerCase());
                } else {
                    int lastButOneIndex = nameBeforeTheLastAt.lastIndexOf("@");
                    if (lastButOneIndex > -1) {
                        String potentialWebFingerId = uniqueName.substring(lastButOneIndex + 1);
                        if (isWebFingerIdValid(potentialWebFingerId)) {
                            return Optional.of(potentialWebFingerId.toLowerCase());
                        }
                    }
                }
            } else {
                return Optional.of(uniqueName.toLowerCase() + "@" +
                        origin.fixUriForPermalink(UriUtils.fromUrl(origin.getUrl())).getHost());
            }
        }
        return Optional.empty();
    }

    public Actor setUsername(String username) {
        if (this == EMPTY) {
            throw new IllegalStateException("Cannot set username of EMPTY Actor");
        }
        this.username = StringUtil.isEmpty(username) ? "" : username.trim();
        return this;
    }

    public String getProfileUrl() {
        return profileUri.toString();
    }

    public Actor setProfileUrl(String url) {
        this.profileUri = UriUtils.fromString(url);
        return this;
    }

    public void setProfileUrlToOriginUrl(URL originUrl) {
        profileUri = UriUtils.fromUrl(originUrl);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Actor)) return false;
        return isSame((Actor) o, true);
    }

    @Override
    public int hashCode() {
        int result = origin.hashCode();
        if (actorId != 0) {
            return 31 * result + Long.hashCode(actorId);
        }
        if (UriUtils.isRealOid(oid)) {
            return 31 * result + oid.hashCode();
        } else if (isWebFingerIdValid) {
            return 31 * result + getWebFingerId().hashCode();
        } else if (isUsernameValid()) {
            result = 31 * result + getUsername().hashCode();
        }
        return result;
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
        if (isWebFingerIdValid()) {
            if (webFingerId.equals(other.webFingerId)) return true;
            if (other.isWebFingerIdValid) return false;
        }
        if (!this.groupType.isSameActor(other.groupType)) return false;

        return isUsernameValid() && other.isUsernameValid() && username.equalsIgnoreCase(other.username);
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

    public Actor build() {
        if (this == EMPTY) return this;

        connectionHost.reset();
        if (StringUtil.isEmpty(username) || isWebFingerIdValid) return this;

        if (username.contains("@")) {
            setWebFingerId(username);
        } else if (!UriUtils.isEmpty(profileUri)){
            if(origin.isValid()) {
                setWebFingerId(username + "@" + origin.fixUriForPermalink(profileUri).getHost());
            } else {
                setWebFingerId(username + "@" + profileUri.getHost());
            }
            // Don't infer "id host" from the Origin's host
        }
        return this;
    }

    public Actor setWebFingerId(String webFingerIdIn) {
        if (StringUtil.isEmpty(webFingerIdIn) || !webFingerIdIn.contains("@")) return this;

        String potentialUsername = webFingerIdIn;
        final String nameBeforeTheLastAt = webFingerIdIn.substring(0, webFingerIdIn.lastIndexOf("@"));
        if (isWebFingerIdValid(webFingerIdIn)) {
            webFingerId = webFingerIdIn.toLowerCase();
            isWebFingerIdValid = true;
            potentialUsername = nameBeforeTheLastAt;
        } else {
            int lastButOneIndex = nameBeforeTheLastAt.lastIndexOf("@");
            if (lastButOneIndex > -1) {
                String potentialWebFingerId = webFingerIdIn.substring(lastButOneIndex + 1);
                if (isWebFingerIdValid(potentialWebFingerId)) {
                    webFingerId = potentialWebFingerId.toLowerCase();
                    isWebFingerIdValid = true;
                    potentialUsername = webFingerIdIn.substring(0, lastButOneIndex);
                }
            }
        }
        if (!isUsernameValid() && origin.isUsernameValid(potentialUsername)) {
            username = potentialUsername;
        }
        return this;
    }

    public String getWebFingerId() {
        return webFingerId;
    }

    public boolean isWebFingerIdValid() {
        return isWebFingerIdValid;
    }

    public static boolean isWebFingerIdValid(String webFingerId) {
        return StringUtil.nonEmpty(webFingerId) && Patterns.WEBFINGER_ID_REGEX_PATTERN.matcher(webFingerId).matches();
    }

    public String getBestUri() {
        if (!StringUtil.isEmptyOrTemp(oid)) {
            return oid;
        }
        if (isUsernameValid() && StringUtil.nonEmpty(getIdHost())) {
            return OriginPumpio.ACCOUNT_PREFIX + getUsername() + "@" + getIdHost();
        }
        if (isWebFingerIdValid()) {
            return OriginPumpio.ACCOUNT_PREFIX + getWebFingerId();
        }
        return "";
    }

    /** Lookup the application's id from other IDs */
    public void lookupActorId() {
        if (isConstant()) return;

        if (actorId == 0 && isOidReal()) {
            actorId = MyQuery.oidToId(origin.myContext, OidEnum.ACTOR_OID, origin.getId(), oid);
        }
        if (actorId == 0 && isWebFingerIdValid()) {
            actorId = MyQuery.webFingerIdToId(origin.myContext, origin.getId(), webFingerId, true);
        }
        if (actorId == 0 && StringUtil.nonEmpty(username)) {
            long actorId2 = origin.usernameToId(username);
            if (actorId2 != 0) {
                boolean skip2 = false;
                if (isWebFingerIdValid()) {
                    String webFingerId2 = MyQuery.actorIdToWebfingerId(origin.myContext, actorId2);
                    if (isWebFingerIdValid(webFingerId2)) {
                        skip2 = !webFingerId.equalsIgnoreCase(webFingerId2);
                        if (!skip2) actorId = actorId2;
                    }
                }
                if (actorId == 0 && !skip2 && isOidReal()) {
                    String oid2 = MyQuery.idToOid(origin.myContext, OidEnum.ACTOR_OID, actorId2, 0);
                    if (UriUtils.isRealOid(oid2)) skip2 = !oid.equalsIgnoreCase(oid2);
                }
                if (actorId == 0 && !skip2 && groupType != GroupType.UNKNOWN) {
                    GroupType groupTypeStored = origin.myContext.users().idToGroupType(actorId2);
                    if (Group.groupTypeNeedsCorrection(groupTypeStored, groupType)) {
                        long updatedDateStored = MyQuery.actorIdToLongColumnValue(ActorTable.UPDATED_DATE, actorId);
                        if (this.getUpdatedDate() <= updatedDateStored) {
                            this.setUpdatedDate(Math.max(updatedDateStored + 1, SOME_TIME_AGO + 1));
                        }
                    }
                }
                if (actorId == 0 && !skip2) {
                    actorId = actorId2;
                }
            }
        }
        if (actorId == 0) {
            actorId = MyQuery.oidToId(origin.myContext, OidEnum.ACTOR_OID, origin.getId(), toTempOid());
        }
        if (actorId == 0 && hasAltTempOid()) {
            actorId = MyQuery.oidToId(origin.myContext, OidEnum.ACTOR_OID, origin.getId(), toAltTempOid());
        }
    }

    public boolean hasAltTempOid() {
        return !toTempOid().equals(toAltTempOid()) && StringUtil.nonEmpty(username);
    }

    public boolean hasLatestNote() {
        return latestActivity != null && !latestActivity.isEmpty() ;
    }

    public String toTempOid() {
        return toTempOid(webFingerId, username);
    }

    public String toAltTempOid() {
        return toTempOid("", username);
    }

    public static String toTempOid(String webFingerId, String validUserName) {
        return StringUtil.toTempOid(isWebFingerIdValid(webFingerId) ? webFingerId : validUserName);
    }

    /** Tries to find this actor in the home origin (the same host...).
     * Returns the same Actor, if not found */
    public Actor toHomeOrigin() {
        return origin.getHost().equals(getConnectionHost())
            ? this
            : user.actorIds.stream().map(id -> NullUtil.getOrDefault(origin.myContext.users().actors, id, Actor.EMPTY))
                .filter(a -> a.nonEmpty() && a.origin.getHost().equals(getConnectionHost()))
                .findAny().orElse(this);
    }

    public List<Actor> extractActorsFromContent(String text, Actor inReplyToActorIn) {
        return _extractActorsFromContent(MyHtml.htmlToCompactPlainText(text), 0, new ArrayList<>(),
                inReplyToActorIn.withValidUsernameAndWebfingerId());
    }

    private List<Actor> _extractActorsFromContent(String text, int textStart, List<Actor> actors, Actor inReplyToActor) {
        ActorReference actorReference = origin.getActorReference(text, textStart);
        if (actorReference.index < textStart) return actors;

        String validUsername = "";
        String validWebFingerId = "";
        int ind = actorReference.index;
        for (; ind < text.length(); ind++) {
            if (WEBFINGER_ID_CHARS.indexOf(text.charAt(ind)) < 0 ) {
                break;
            }
            String username = text.substring(actorReference.index, ind + 1);
            if (origin.isUsernameValid(username)) {
                validUsername = username;
            }
            if (isWebFingerIdValid(username)) {
                validWebFingerId = username;
            }
        }
        if (StringUtil.nonEmpty(validWebFingerId) || StringUtil.nonEmpty(validUsername)) {
            addExtractedActor(actors, validWebFingerId, validUsername, actorReference.groupType, inReplyToActor);
        }
        return _extractActorsFromContent(text, ind + 1, actors, inReplyToActor);
    }

    private Actor withValidUsernameAndWebfingerId() {
        return (isWebFingerIdValid && isUsernameValid()) || actorId == 0
            ? this
            : Actor.load(origin.myContext, actorId);
    }

    public boolean isUsernameValid() {
        return StringUtil.nonEmptyNonTemp(username) && origin.isUsernameValid(username);
    }

    private void addExtractedActor(List<Actor> actors, String webFingerId, String validUsername, GroupType groupType,
                                   Actor inReplyToActor) {
        Actor actor = Actor.newUnknown(origin, groupType);
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
                // Don't infer "id host", if it wasn't explicitly provided
                actor.setUsername(validUsername);
            }
        }
        actor.build();
        actors.add(actor);
    }

    public String getConnectionHost() {
        return connectionHost.get();
    }

    private String evalConnectionHost() {
        if (origin.shouldHaveUrl()) {
            return origin.getHost();
        }
        if (StringUtil.nonEmpty(profileUri.getHost())) {
            return profileUri.getHost();
        }
        return UrlUtils.getHost(oid).orElseGet(() -> {
            if (isWebFingerIdValid) {
                int pos = getWebFingerId().indexOf('@');
                if (pos >= 0) {
                    return getWebFingerId().substring(pos + 1);
                }
            }
            return "";
        });
    }

    public String getIdHost() {
        return idHost.get();
    }

    private String evalIdHost() {
        return UrlUtils.getHost(oid).orElseGet(() -> {
            if (isWebFingerIdValid) {
                int pos = getWebFingerId().indexOf('@');
                if (pos >= 0) {
                    return getWebFingerId().substring(pos + 1);
                }
            }
            if (StringUtil.nonEmpty(profileUri.getHost())) {
                return profileUri.getHost();
            }
            return "";
        });
    }


    public String getSummary() {
        return summary;
    }

    public Actor setSummary(String summary) {
        if (!isEmpty() && !SharedPreferencesUtil.isEmpty(summary)) {
            this.summary = summary;
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

    public String getRecipientName() {
        if (groupType == GroupType.FOLLOWERS) {
            return origin.myContext.context().getText(R.string.followers).toString();
        }
        return getUniqueName();
    }

    public String getViewItemActorName() {
        if (MyPreferences.getShowOrigin() && nonEmpty()) {
            String name = getTimelineUsername() + " / " + origin.getName();
            if (origin.getOriginType() == OriginType.GNUSOCIAL && MyPreferences.isShowDebuggingInfoInUi()
                    && StringUtil.nonEmpty(oid)) {
                return name + " oid:" + oid;
            } else return name;
        } else return getTimelineUsername();
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
        this.createdDate = createdDate < SOME_TIME_AGO ? SOME_TIME_AGO : createdDate;
    }

    public long getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(long updatedDate) {
        if  (this.updatedDate >= updatedDate) return;

        this.updatedDate = updatedDate < SOME_TIME_AGO ? SOME_TIME_AGO : updatedDate;
    }

    @Override
    public int compareTo(@NonNull Actor another) {
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

    public String toActorTitle() {
        StringBuilder builder = new StringBuilder();
        final String uniqueName = getUniqueName();
        if (StringUtil.nonEmpty(uniqueName)) {
            builder.append("@" + uniqueName);
        }
        if (StringUtil.nonEmpty(getRealName())) {
            MyStringBuilder.appendWithSpace(builder, "(" + getRealName() + ")");
        }
        return builder.toString();
    }

    public String getTimelineUsername() {
        String name1 = getTimelineUsername1();
        return StringUtil.nonEmpty(name1) ? name1 : getUniqueNameWithOrigin();
    }

    private String getTimelineUsername1() {
        switch (MyPreferences.getActorInTimeline()) {
            case AT_USERNAME:
                return StringUtil.isEmpty(username) ? "" : "@" + username;
            case WEBFINGER_ID:
                return isWebFingerIdValid ? webFingerId : "";
            case REAL_NAME:
                return realName;
            case REAL_NAME_AT_USERNAME:
                return StringUtil.nonEmpty(realName) && StringUtil.nonEmpty(username)
                    ? realName + " @" + username
                    : username;
            case REAL_NAME_AT_WEBFINGER_ID:
                return StringUtil.nonEmpty(realName) && StringUtil.nonEmpty(webFingerId)
                        ? realName + " @" + webFingerId
                        : webFingerId;
            default:
                return username;
        }
    }

    public Actor lookupUser() {
        return origin.myContext.users().lookupUser(this);
    }

    public void saveUser() {
        if (user.isMyUser().unknown && origin.myContext.users().isMe(this)) {
            user.setIsMyUser(TriState.TRUE);
        }
        if (user.userId == 0) user.setKnownAs(getUniqueName());
        user.save(origin.myContext);
    }

    public boolean hasAvatar() {
        return UriUtils.nonEmpty(avatarUri);
    }

    public boolean hasAvatarFile() {
        return AvatarFile.EMPTY != avatarFile;
    }

    public void requestDownload(boolean isManuallyLaunched) {
        if (canGetActor()) {
            MyLog.v(this, () -> "Actor " + this + " will be loaded from the Internet");
            CommandData command = CommandData.newActorCommandAtOrigin(
                    CommandEnum.GET_ACTOR, this, getUsername(), origin)
                .setManuallyLaunched(isManuallyLaunched);
            MyServiceManager.sendForegroundCommand(command);
        } else {
            MyLog.v(this, () -> "Cannot get Actor " + this);
        }
    }

    public boolean isPublic() {
        return groupType == GroupType.PUBLIC;
    }

    public boolean isFollowers() {
        return groupType == GroupType.FOLLOWERS;
    }

    public boolean nonPublic() {
        return !isPublic();
    }

    public Uri getAvatarUri() {
        return avatarUri;
    }

    public String getAvatarUrl() {
        return avatarUri.toString();
    }

    public void setAvatarUrl(String avatarUrl) {
        setAvatarUri(UriUtils.fromString(avatarUrl));
    }

    public void setAvatarUri(Uri avatarUri) {
        this.avatarUri = UriUtils.notNull(avatarUri);
        if (hasAvatar() && avatarFile.isEmpty()) {
            avatarFile = AvatarFile.fromActorOnly(this);
        }
    }

    public void requestAvatarDownload() {
        if (hasAvatar() && MyPreferences.getShowAvatars() && avatarFile.downloadStatus != DownloadStatus.LOADED) {
            avatarFile.requestDownload();
        }
    }

    public Optional<Uri> getEndpoint(ActorEndpointType type) {
        Optional<Uri> uri = endpoints.findFirst(type);
        return uri.isPresent() ? uri
                : type == ActorEndpointType.API_PROFILE
                    ? UriUtils.toDownloadableOptional(oid)
                    : Optional.empty();
    }

    public void assertContext() {
        if (isConstant()) return;

        try {
            origin.assertContext();
        } catch (Throwable e) {
            fail("Failed on " + this + "\n" + e.getMessage());
        }
    }


    public Actor setParentActorId(MyContext myContext, long parentActorId) {
        if (this.parentActorId != parentActorId) {
            this.parentActorId = parentActorId;
            this.parentActor = parentActorId == 0
                    ? LazyVal.of(() -> Actor.EMPTY)
                    : LazyVal.of(() -> Actor.load(myContext, parentActorId));
        }
        return this;
    }

    public long getParentActorId() {
        return parentActorId;
    }

    public Actor getParent() {
        return parentActor.get();
    }
}
