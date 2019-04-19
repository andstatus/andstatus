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

import org.andstatus.app.account.AccountName;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.ActorSql;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.user.User;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.LazyVal;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import androidx.annotation.NonNull;

import static org.andstatus.app.net.social.Patterns.USERNAME_CHARS;
import static org.andstatus.app.net.social.Patterns.WEBFINGER_ID_CHARS;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

/**
 * @author yvolk@yurivolkov.com
 */
public class Actor implements Comparable<Actor>, IsEmpty {
    public static final Actor EMPTY = newUnknown(Origin.EMPTY).setUsername("Empty");
    public static final Actor PUBLIC = fromOid(Origin.EMPTY,
            "https://www.w3.org/ns/activitystreams#Public").setUsername("Public");

    @NonNull
    public final String oid;
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
    private final LazyVal<String> host = LazyVal.of(this::evalGetHost);

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
    public volatile long actorId = 0L;
    public AvatarFile avatarFile = AvatarFile.EMPTY;

    public User user = User.EMPTY;

    private volatile TriState isPartiallyDefined = TriState.UNKNOWN;

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
        return MyAsyncTask.nonUiThread() && (cached.isPartiallyDefined() || reloadFirst)
                ? loadFromDatabase(myContext, actorId, supplier).betterToCache(cached)
                : cached;
    }

    private Actor betterToCache(Actor other) {
        return isBetterToCacheThan(other) ?  this : other;
    }

    private static Actor loadFromDatabase(@NonNull MyContext myContext, long actorId, Supplier<Actor> supplier) {
        final String sql = "SELECT " + ActorSql.select()
                + " FROM " + ActorSql.tables()
                + " WHERE " + ActorTable.TABLE_NAME + "." + ActorTable._ID + "=" + actorId;
        final Function<Cursor, Actor> function = cursor -> fromCursor(myContext, cursor);
        return MyQuery.get(myContext, sql, function).stream().findFirst().orElseGet(supplier);
    }

    /** Updates cache on load */
    @NonNull
    public static Actor fromCursor(MyContext myContext, Cursor cursor) {
        final long updatedDate = DbUtils.getLong(cursor, ActorTable.UPDATED_DATE);
        Actor actor = Actor.fromTwoIds(
                myContext.origins().fromId(DbUtils.getLong(cursor, ActorTable.ORIGIN_ID)),
                    DbUtils.getLong(cursor, ActorTable.ACTOR_ID),
                    DbUtils.getString(cursor, ActorTable.ACTOR_OID));
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

        actor.user = User.fromCursor(myContext, cursor);
        actor.avatarFile = AvatarFile.fromCursor(actor, cursor);
        Actor cachedActor = myContext.users().actors.getOrDefault(actor.actorId, Actor.EMPTY);
        if (actor.isBetterToCacheThan(cachedActor)) {
            myContext.users().updateCache(actor);
            return actor;
        }
        return cachedActor;
    }

    public static Actor newUnknown(@NonNull Origin origin) {
        return fromTwoIds(origin, 0, "");
    }

    public static Actor fromId(@NonNull Origin origin, long actorId) {
        return fromTwoIds(origin, actorId, "");
    }

    public static Actor fromOid(@NonNull Origin origin, String actorOid) {
        return fromTwoIds(origin, 0, actorOid);
    }

    public static Actor fromTwoIds(@NonNull Origin origin, long actorId, String actorOid) {
        return new Actor(origin, actorId, actorOid);
    }

    private Actor(@NonNull Origin origin, long actorId, String actorOid) {
        this.origin = origin;
        this.actorId = actorId;
        this.oid = StringUtils.isEmpty(actorOid) ? "" : actorOid;
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

    @Override
    public boolean isEmpty() {
        return this == EMPTY || !origin.isValid() ||
                (actorId == 0 && UriUtils.nonRealOid(oid) && StringUtils.isEmpty(webFingerId) && !isUsernameValid());
    }

    public boolean isPartiallyDefined() {
        if (isPartiallyDefined.unknown) {
            isPartiallyDefined = TriState.fromBoolean(!origin.isValid() || UriUtils.nonRealOid(oid) ||
                    StringUtils.isEmpty(webFingerId) ||
                    !isUsernameValid());
        }
        return isPartiallyDefined.isTrue;
    }

    public boolean isBetterToCacheThan(Actor other) {
        if (this == other) return false;

        if (other == null || other == EMPTY ||
                (!isPartiallyDefined() && other.isPartiallyDefined())) return true;

        if (getUpdatedDate() != other.getUpdatedDate()) {
            return getUpdatedDate() > other.getUpdatedDate();
        }
        if (avatarFile.downloadedDate != other.avatarFile.downloadedDate) {
            return avatarFile.downloadedDate > other.avatarFile.downloadedDate;
        }
        return notesCount > other.notesCount;
    }

    public boolean isIdentified() {
        return actorId != 0 && isOidReal();
    }

    public boolean isOidReal() {
        return UriUtils.isRealOid(oid);
    }

    public boolean canGetActor() {
        return (isOidReal() || isUsernameValid()) && StringUtils.nonEmpty(getHost());
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "Actor:EMPTY";
        }
        MyStringBuilder members = MyStringBuilder.of("origin:" + origin.getName())
        .withComma("id", actorId)
        .withComma("oid", oid)
        .withComma(isWebFingerIdValid() ? "webFingerId" : "", StringUtils.isEmpty(webFingerId)
                ? ""
                : isWebFingerIdValid() ? webFingerId : "(invalid webFingerId)")
        .withComma("username", username)
        .withComma("realName", realName)
        .withComma("", user, user::nonEmpty)
        .withComma("profileUri", profileUri, UriUtils::nonEmpty)
        .withComma("avatar", avatarUri, this::hasAvatar)
        .withComma("avatarFile", avatarFile, this::hasAvatarFile)
        .withComma("banner", endpoints.findFirst(ActorEndpointType.BANNER).orElse(null))
        .withComma("", "latest note present", this::hasLatestNote);
        return MyLog.formatKeyValue(this, members);
    }

    public String getUsername() {
        return username;
    }

    public String getUniqueNameWithOrigin() {
        return getUniqueNameInOrigin() + AccountName.ORIGIN_SEPARATOR + origin.getName();
    }

    public String getUniqueNameInOrigin() {
        if (StringUtils.nonEmptyNonTemp(username)) return username + getOptAtHostForUniqueName();
        if (StringUtils.nonEmptyNonTemp(realName)) return realName + getOptAtHostForUniqueName();
        if (StringUtils.nonEmptyNonTemp(oid)) return oid;
        return "id:" + actorId + getOptAtHostForUniqueName();
    }

    private String getOptAtHostForUniqueName() {
        return origin.getOriginType().uniqueNameHasHost() ? "@" + getHost() : "";
    }

    public Actor withUniqueNameInOrigin(String uniqueNameInOrigin) {
        uniqueNameInOriginToUsername(origin, uniqueNameInOrigin).ifPresent(this::setUsername);
        uniqueNameInOriginToWebFingerId(uniqueNameInOrigin).ifPresent(this::setWebFingerId);
        return this;
    }

    public static Optional<String> uniqueNameInOriginToUsername(Origin origin, String uniqueNameInOrigin) {
        if (StringUtils.nonEmpty(uniqueNameInOrigin)) {
            if (uniqueNameInOrigin.contains("@")) {
                final String nameBeforeTheLastAt = uniqueNameInOrigin.substring(0, uniqueNameInOrigin.lastIndexOf("@"));
                if (isWebFingerIdValid(uniqueNameInOrigin)) {
                    if (origin.isUsernameValid(nameBeforeTheLastAt)) return Optional.of(nameBeforeTheLastAt);
                } else {
                    int lastButOneIndex = nameBeforeTheLastAt.lastIndexOf("@");
                    if (lastButOneIndex > -1) {
                        // A case when a Username contains "@"
                        String potentialWebFingerId = uniqueNameInOrigin.substring(lastButOneIndex+1);
                        if (isWebFingerIdValid(potentialWebFingerId)) {
                            final String nameBeforeLastButOneAt = uniqueNameInOrigin.substring(0, lastButOneIndex);
                            if (origin.isUsernameValid(nameBeforeLastButOneAt)) return Optional.of(nameBeforeLastButOneAt);
                        }
                    }
                }
            }
            if (origin.isUsernameValid(uniqueNameInOrigin)) return Optional.of(uniqueNameInOrigin);
        }
        return Optional.empty();
    }

    public Optional<String> uniqueNameInOriginToWebFingerId(String uniqueNameInOrigin) {
        if (StringUtils.nonEmpty(uniqueNameInOrigin)) {
            if (!origin.getOriginType().uniqueNameHasHost() && origin.shouldHaveUrl()) {
                return Optional.of(uniqueNameInOrigin.toLowerCase() + "@" +
                        origin.fixUriForPermalink(UriUtils.fromUrl(origin.getUrl())).getHost());
            }
            if (uniqueNameInOrigin.contains("@")) {
                final String nameBeforeTheLastAt = uniqueNameInOrigin.substring(0, uniqueNameInOrigin.lastIndexOf("@"));
                if (isWebFingerIdValid(uniqueNameInOrigin)) {
                    return Optional.of(uniqueNameInOrigin.toLowerCase());
                } else {
                    int lastButOneIndex = nameBeforeTheLastAt.lastIndexOf("@");
                    if (lastButOneIndex > -1) {
                        String potentialWebFingerId = uniqueNameInOrigin.substring(lastButOneIndex + 1);
                        if (isWebFingerIdValid(potentialWebFingerId)) {
                            return Optional.of(potentialWebFingerId.toLowerCase());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public Actor setUsername(String username) {
        if (this == EMPTY) {
            throw new IllegalStateException("Cannot set username of EMPTY Actor");
        }
        this.username = StringUtils.isEmpty(username) ? "" : username.trim();
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

    public Actor build() {
        if (this == EMPTY) return this;

        host.reset();
        if (StringUtils.isEmpty(username) || isWebFingerIdValid) return this;

        if (username.contains("@")) {
            setWebFingerId(username);
        } else if (!UriUtils.isEmpty(profileUri)){
            if(origin.isValid()) {
                setWebFingerId(username + "@" + origin.fixUriForPermalink(profileUri).getHost());
            } else {
                setWebFingerId(username + "@" + profileUri.getHost());
            }
        } else if (origin.shouldHaveUrl()) {
            setWebFingerId(username + "@" + origin.fixUriForPermalink(UriUtils.fromUrl(origin.getUrl())).getHost());
        }
        return this;
    }

    public Actor setWebFingerId(String webFingerIdIn) {
        if (StringUtils.isEmpty(webFingerIdIn) || !webFingerIdIn.contains("@")) return this;

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
        return StringUtils.nonEmpty(webFingerId) && Patterns.WEBFINGER_ID_REGEX_PATTERN.matcher(webFingerId).matches();
    }

    public String getBestUri() {
        if (!StringUtils.isEmptyOrTemp(oid)) {
            return oid;
        }
        if (isUsernameValid() && StringUtils.nonEmpty(getHost())) {
            return OriginPumpio.ACCOUNT_PREFIX + getUsername() + "@" + getHost();
        }
        if (isWebFingerIdValid()) {
            return OriginPumpio.ACCOUNT_PREFIX + getWebFingerId();
        }
        return "";
    }

    /** Lookup the application's id from other IDs */
    public void lookupActorId() {
        if (actorId == 0 && isOidReal()) {
            actorId = MyQuery.oidToId(origin.myContext, OidEnum.ACTOR_OID, origin.getId(), oid);
        }
        if (actorId == 0 && isWebFingerIdValid()) {
            actorId = MyQuery.webFingerIdToId(origin.getId(), webFingerId);
        }
        if (actorId == 0 && StringUtils.nonEmpty(username)) {
            long actorId2 = MyQuery.usernameToId(origin.getId(), username);
            if (actorId2 != 0) {
                boolean skip2 = false;
                if (isWebFingerIdValid()) {
                    String webFingerId2 = MyQuery.actorIdToWebfingerId(actorId2);
                    if (isWebFingerIdValid(webFingerId2)) {
                        skip2 = !webFingerId.equalsIgnoreCase(webFingerId2);
                        if (!skip2) actorId = actorId2;
                    }
                }
                if (actorId == 0 && !skip2 && isOidReal()) {
                    String oid2 = MyQuery.idToOid(OidEnum.ACTOR_OID, actorId2, 0);
                    if (UriUtils.isRealOid(oid2)) skip2 = !oid.equalsIgnoreCase(oid2);
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
        return !toTempOid().equals(toAltTempOid()) && StringUtils.nonEmpty(username);
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
        return StringUtils.toTempOid(isWebFingerIdValid(webFingerId) ? webFingerId : validUserName);
    }

    public List<Actor> extractActorsFromContent(String text, Actor inReplyToActorIn) {
        return _extractActorsFromContent(MyHtml.htmlToCompactPlainText(text), 0, new ArrayList<>(),
                inReplyToActorIn.withValidUsernameAndWebfingerId());
    }

    private List<Actor> _extractActorsFromContent(String text, int textStart, List<Actor> actors, Actor inReplyToActor) {
        int start = indexOfActorReference(text, textStart);
        if (start < textStart) return actors;

        String validUsername = "";
        String validWebFingerId = "";
        int ind = start;
        for (; ind < text.length(); ind++) {
            if (WEBFINGER_ID_CHARS.indexOf(text.charAt(ind)) < 0 ) {
                break;
            }
            String username = text.substring(start, ind + 1);
            if (origin.isUsernameValid(username)) {
                validUsername = username;
            }
            if (isWebFingerIdValid(username)) {
                validWebFingerId = username;
            }
        }
        if (StringUtils.nonEmpty(validWebFingerId) || StringUtils.nonEmpty(validUsername)) {
            addExtractedActor(actors, validWebFingerId, validUsername, inReplyToActor);
        }
        return _extractActorsFromContent(text, ind + 1, actors, inReplyToActor);
    }

    private Actor withValidUsernameAndWebfingerId() {
        return (isWebFingerIdValid && isUsernameValid()) || actorId == 0
            ? this
            : Actor.load(origin.myContext, actorId);
    }

    public boolean isUsernameValid() {
        return StringUtils.nonEmptyNonTemp(username) && origin.isUsernameValid(username);
    }

    /**
     * The reference may be in the form of @username, @webfingerId, or wibfingerId, without "@" before it
     * @return index of the first position, where the username/webfingerId may start, -1 if not found
     */
    private int indexOfActorReference(String text, int textStart) {
        if (StringUtils.isEmpty(text) || textStart >= text.length()) return -1;

        int indexOfAt = text.indexOf('@', textStart);
        if (indexOfAt < textStart) return -1;

        if (indexOfAt == textStart) return textStart + 1;

        if (USERNAME_CHARS.indexOf(text.charAt(indexOfAt - 1)) < 0) return indexOfAt + 1;

        // username part of WebfingerId before @ ?
        int ind = indexOfAt - 1;
        while (ind > textStart) {
            if (USERNAME_CHARS.indexOf(text.charAt(ind - 1)) < 0) break;
            ind--;
        }
        return ind;
    }

    private void addExtractedActor(List<Actor> actors, String webFingerId, String validUsername, Actor inReplyToActor) {
        Actor actor = Actor.newUnknown(origin);
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
                // Try 1. a host of the Author (this Actor), 2. A host of Replied to Actor, 3. A host of this Social network
                for (String host : new HashSet<>(Arrays.asList(getHost(), inReplyToActor.getHost(),
                        origin.getHost()))) {
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
        actor.build();
        actor.lookupActorId();
        if (!actors.contains(actor)) {
            actors.add(actor);
        }
    }

    public String getHost() {
        return host.get();
    }

    private String evalGetHost() {
        if (isWebFingerIdValid) {
            int pos = getWebFingerId().indexOf('@');
            if (pos >= 0) {
                return getWebFingerId().substring(pos + 1);
            }
        }
        if (origin.shouldHaveUrl()) {
            return origin.getHost();
        }
        return UrlUtils.getHost(oid).orElse(StringUtils.nonEmpty(profileUri.getHost()) ? profileUri.getHost() : "");
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

    public String toActorTitle(boolean showWebFingerId) {
        StringBuilder builder = new StringBuilder();
        if (showWebFingerId && StringUtils.nonEmpty(getWebFingerId())) {
            builder.append(getWebFingerId());
        } else {
            final String uniqueNameInOrigin = getUniqueNameInOrigin();
            if (StringUtils.nonEmpty(uniqueNameInOrigin)) {
                builder.append("@" + uniqueNameInOrigin);
            }
        }
        if (StringUtils.nonEmpty(getRealName())) {
            MyStringBuilder.appendWithSpace(builder, "(" + getRealName() + ")");
        }
        return builder.toString();
    }

    public String getTimelineUsername() {
        String name1 = getTimelineUsername1();
        return StringUtils.nonEmpty(name1) ? name1 : getUniqueNameWithOrigin();
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
            case REAL_NAME_AT_WEBFINGER_ID:
                return StringUtils.nonEmpty(realName) && StringUtils.nonEmpty(webFingerId)
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
        if (user.userId == 0) user.setKnownAs(getUniqueNameWithOrigin());
        user.save(origin.myContext);
    }

    public boolean hasAvatar() {
        return UriUtils.nonEmpty(avatarUri);
    }

    public boolean hasAvatarFile() {
        return AvatarFile.EMPTY != avatarFile;
    }

    public void requestDownload() {
        if (canGetActor()) {
            MyLog.v(this, () -> "Actor " + this + " will be loaded from the Internet");
            MyServiceManager.sendForegroundCommand(
                    CommandData.newActorCommand(CommandEnum.GET_ACTOR, actorId, getUsername()));
        } else {
            MyLog.v(this, () -> "Cannot get Actor " + this);
        }
    }

   public boolean isPublic() {
        return PUBLIC.equals(this);
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
        if (MyPreferences.getShowAvatars() && hasAvatar() && avatarFile.downloadStatus != DownloadStatus.LOADED) {
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
}
