package org.andstatus.app.net;

import android.text.TextUtils;

import org.andstatus.app.origin.OriginConnectionData;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1
 *
 */
public class ConnectionTwitter1p1 extends ConnectionTwitter {

    public ConnectionTwitter1p1(OriginConnectionData connectionData) {
        super(connectionData);
    }

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = "application/rate_limit_status" + EXTENSION;
                break;
            case CREATE_FAVORITE:
                url = "favorites/create" + EXTENSION;
                break;
            case DESTROY_FAVORITE:
                url = "favorites/destroy" + EXTENSION;
                break;
            /** https://dev.twitter.com/docs/api/1.1/get/statuses/mentions_timeline */
            case STATUSES_MENTIONS_TIMELINE:
                url = "statuses/mentions_timeline" + EXTENSION;
                break;
            default:
                url = "";
        }
        if (TextUtils.isEmpty(url)) {
            url = super.getApiPath1(routine);
        } else {
            url = httpConnection.connectionData.basicPath + "/" + url;
        }
        return url;
    }

    @Override
    public MbMessage createFavorite(String statusId) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("id", statusId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JSONObject jso = postRequest(ApiRoutineEnum.CREATE_FAVORITE, out);
        return messageFromJson(jso);
    }

    @Override
    public MbMessage destroyFavorite(String statusId) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("id", statusId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JSONObject jso = postRequest(ApiRoutineEnum.DESTROY_FAVORITE, out);
        return messageFromJson(jso);
    }
}
