package org.andstatus.app.net;

import org.andstatus.app.origin.OriginConnectionData;
import org.json.JSONObject;

/**
 * Twitter API v.1 https://dev.twitter.com/docs/api/1
 *
 */
public class ConnectionTwitter1p0 extends ConnectionTwitter {

    public ConnectionTwitter1p0(OriginConnectionData connectionData) {
        super(connectionData);
    }

    @Override
    public JSONObject createFavorite(String statusId) throws ConnectionException {
        StringBuilder path = new StringBuilder(getApiPath(ApiRoutineEnum.FAVORITES_CREATE_BASE));
        path.append(statusId);
        path.append(EXTENSION);
        return httpConnection.postRequest(path.toString());
    }

    @Override
    public JSONObject destroyFavorite(String statusId) throws ConnectionException {
        StringBuilder path = new StringBuilder(getApiPath(ApiRoutineEnum.FAVORITES_DESTROY_BASE));
        path.append(statusId);
        path.append(EXTENSION);
        return httpConnection.postRequest(path.toString());
    }
}
