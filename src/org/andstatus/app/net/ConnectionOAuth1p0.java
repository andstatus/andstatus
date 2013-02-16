package org.andstatus.app.net;

import org.andstatus.app.account.MyAccount;
import org.apache.http.client.methods.HttpPost;
import org.json.JSONObject;

/**
 * Twitter API v.1 https://dev.twitter.com/docs/api/1
 *
 */
public class ConnectionOAuth1p0 extends ConnectionOAuth {

    public ConnectionOAuth1p0(MyAccount ma, ApiEnum api, String apiBaseUrl, String apiOauthBaseUrl) {
        super(ma, api, apiBaseUrl, apiOauthBaseUrl);
    }

    @Override
    public JSONObject createFavorite(String statusId) throws ConnectionException {
        StringBuilder url = new StringBuilder(getApiUrl(ApiRoutineEnum.FAVORITES_CREATE_BASE));
        url.append(statusId);
        url.append(EXTENSION);
        HttpPost post = new HttpPost(url.toString());
        return postRequest(post);
    }
    
    @Override
    public JSONObject destroyFavorite(String statusId) throws ConnectionException {
        HttpPost post = new HttpPost(getApiUrl(ApiRoutineEnum.FAVORITES_DESTROY_BASE) + statusId + EXTENSION);
        return postRequest(post);
    }
}
