/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social.activitypub;

import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio;
import org.andstatus.app.util.JsonUtils;
import org.json.JSONObject;

import androidx.annotation.NonNull;

public class ConnectionActivityPub extends ConnectionPumpio {
    private static final String TAG = ConnectionActivityPub.class.getSimpleName();
    static final String NAME_PROPERTY = "name";

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "ap/whoami";
                break;
            case PUBLIC_TIMELINE:
                url = "inbox";
                break;
            default:
                return super.getApiPath1(routine);
        }
        return prependWithBasicPath(url);
    }

    @NonNull
    protected Actor actorFromJson(JSONObject jso) {
        if (!ApObjectType.PERSON.isTypeOf(jso)) {
            return Actor.EMPTY;
        }
        String oid = jso.optString("id");
        Actor actor = Actor.fromOid(data.getOrigin(), oid);
        actor.setUsername(jso.optString("preferredUsername"));
        actor.setRealName(jso.optString(NAME_PROPERTY));
        actor.setAvatarUrl(JsonUtils.optStringInside(jso, "icon", "url"));
        actor.location = JsonUtils.optStringInside(jso, "location", NAME_PROPERTY);
        actor.setSummary(jso.optString("summary"));
        actor.setHomepage(jso.optString("url"));
        actor.setProfileUrl(jso.optString("url"));
        actor.setUpdatedDate(dateFromJson(jso, "updated"));
        actor.endpoints
            .add(ActorEndpointType.API_PROFILE, jso.optString("id"))
            .add(ActorEndpointType.API_INBOX, jso.optString("inbox"))
            .add(ActorEndpointType.API_OUTBOX, jso.optString("outbox"))
            .add(ActorEndpointType.API_FOLLOWING, jso.optString("following"))
            .add(ActorEndpointType.API_FOLLOWERS, jso.optString("followers"))
            .add(ActorEndpointType.BANNER, JsonUtils.optStringInside(jso, "image", "url"))
            .add(ActorEndpointType.API_SHARED_INBOX, JsonUtils.optStringInside(jso, "endpoints", "sharedInbox"));
        return actor;
    }

}
