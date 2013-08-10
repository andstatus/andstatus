package org.andstatus.app.net;

import org.andstatus.app.origin.OriginConnectionData;
import org.json.JSONArray;
import org.json.JSONObject;

class ConnectionPumpio extends Connection {

    public ConnectionPumpio(OriginConnectionData connectionData) {
        super(connectionData);
        // TODO Auto-generated constructor stub
    }

    protected static Connection fromConnectionDataProtected(OriginConnectionData connectionData) {
        return new ConnectionPumpio(connectionData);
    }

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject rateLimitStatus() throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject verifyCredentials() throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject destroyFavorite(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject createFavorite(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject destroyStatus(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONArray getFriendsIds(String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject getStatus(String statusId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject updateStatus(String message, String inReplyToId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject postDirectMessage(String message, String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject postReblog(String rebloggedId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONArray getTimeline(ApiRoutineEnum apiRoutine, String sinceId, int limit, String userId)
            throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject followUser(String userId, Boolean follow) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JSONObject getUser(String userId) throws ConnectionException {
        // TODO Auto-generated method stub
        return null;
    }
}
