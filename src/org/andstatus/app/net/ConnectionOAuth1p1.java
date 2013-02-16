package org.andstatus.app.net;

import org.andstatus.app.account.MyAccount;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.util.LinkedList;

/**
 * Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1
 *
 */
public class ConnectionOAuth1p1 extends ConnectionOAuth {

    public ConnectionOAuth1p1(MyAccount ma, ApiEnum api, String apiBaseUrl, String apiOauthBaseUrl) {
        super(ma, api, apiBaseUrl, apiOauthBaseUrl);
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
        HttpPost post = new HttpPost(getApiUrl(ApiRoutineEnum.FAVORITES_CREATE_BASE));
        LinkedList<BasicNameValuePair> out = new LinkedList<BasicNameValuePair>();
        out.add(new BasicNameValuePair("id", statusId));
        return postRequest(post, out);
    }

    @Override
    public JSONObject destroyFavorite(String statusId) throws ConnectionException {
        HttpPost post = new HttpPost(getApiUrl(ApiRoutineEnum.FAVORITES_DESTROY_BASE));
        LinkedList<BasicNameValuePair> out = new LinkedList<BasicNameValuePair>();
        out.add(new BasicNameValuePair("id", statusId));
        return postRequest(post, out);
    }
}
