package org.andstatus.app.net;

import org.andstatus.app.account.AccountDataReader;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;

/**
 * Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1
 *
 */
public class ConnectionOAuth1p1 extends ConnectionOAuth {

    public ConnectionOAuth1p1(AccountDataReader dr, ApiEnum api, String apiBaseUrl, String apiOauthBaseUrl) {
        super(dr, api, apiBaseUrl, apiOauthBaseUrl);
    }

    @Override
    protected String getApiUrl1(ApiRoutineEnum routine) {
        String url = "";
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = getBaseUrl()  + "/application/rate_limit_status" + EXTENSION;
                break;
            case FAVORITES_CREATE_BASE:
                url = getBaseUrl() + "/favorites/create" + EXTENSION;
                break;
            case FAVORITES_DESTROY_BASE:
                url = getBaseUrl() + "/favorites/destroy" + EXTENSION;
                break;
            /** https://dev.twitter.com/docs/api/1.1/get/statuses/mentions_timeline */
            case STATUSES_MENTIONS_TIMELINE:
                url = getBaseUrl()  + "/statuses/mentions_timeline" + EXTENSION;
                break;
            default:
                url = super.getApiUrl1(routine);
        }
        return url;
    }

    @Override
    public JSONObject createFavorite(String statusId) throws ConnectionException {
        List<NameValuePair> out = new LinkedList<NameValuePair>();
        out.add(new BasicNameValuePair("id", statusId));
        return postRequest(ApiRoutineEnum.FAVORITES_CREATE_BASE, out);
    }

    @Override
    public JSONObject destroyFavorite(String statusId) throws ConnectionException {
        List<NameValuePair> out = new LinkedList<NameValuePair>();
        out.add(new BasicNameValuePair("id", statusId));
        return postRequest(ApiRoutineEnum.FAVORITES_DESTROY_BASE, out);
    }
}
